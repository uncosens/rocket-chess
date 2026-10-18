package com.uncosens.rocketchess.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.PgnException
import com.uncosens.rocketchess.data.AppDatabase
import com.uncosens.rocketchess.data.GameSummary
import com.uncosens.rocketchess.data.KnownUsers
import com.uncosens.rocketchess.data.SavedGame
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.saveOrFind
import com.uncosens.rocketchess.remote.ChessCom
import com.uncosens.rocketchess.remote.FetchError
import com.uncosens.rocketchess.remote.GamePager
import com.uncosens.rocketchess.remote.Lichess
import com.uncosens.rocketchess.remote.RemoteGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    /** [loading] while the next page is on its way; [moreError] if it failed. */
    data class Found(val games: List<RemoteGame>, val done: Boolean, val loading: Boolean = false, val moreError: FetchError? = null) : OnlineState
    data class Failed(val error: FetchError) : OnlineState
}

/** Opens a game: PGN, source key, owner (searched user) and saved analysis text. */
typealias OpenGame = (pgn: String, key: String, owner: String?, analysis: String?) -> Unit

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).games()

    val games = dao.summaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<GameSummary>())
    /** Saved accuracy by source key, to mark analyzed games in the online list. */
    val accuracies = dao.accuracies().map { list -> list.associateBy { it.sourceKey } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var error by mutableStateOf<LoadError?>(null)
        private set
    var online by mutableStateOf<OnlineState>(OnlineState.Idle)
        private set
    private var fetchJob: Job? = null
    private var pager: GamePager? = null

    /** The name behind the online results shown now. */
    private var searched: String? = null

    init {
        // Names an older version kept for all games become the owner of the games they played.
        val old = KnownUsers.oldNames(app)
        if (old.isNotEmpty()) viewModelScope.launch {
            old.forEach { dao.claimOwner(it) }
            KnownUsers.forgetOldNames(app)
        }
    }

    /** Checks the PGN, saves it (or finds the saved copy) and passes its text, key, [owner] and saved analysis to [open]. */
    private suspend fun save(pgn: String, source: String, key: String, open: OpenGame, owner: String? = null) {
        val game = withContext(Dispatchers.Default) {
            runCatching { Pgn.parse(pgn).also { GameLine.from(it) } }
        }.getOrElse {
            error = if (it is PgnException) LoadError.Parse(it) else LoadError.Unreadable
            return
        }
        val t = game.tags
        dao.saveOrFind(
            SavedGame(
                source = source, sourceKey = key, pgn = pgn, importedAt = System.currentTimeMillis(),
                white = t["White"] ?: "?", black = t["Black"] ?: "?", result = game.result ?: t["Result"] ?: "*", date = t["Date"] ?: "",
                owner = owner,
            ),
        )
        // A copy saved earlier without an owner gets one now.
        owner?.let { dao.setOwnerIfMissing(key, it) }
        error = null
        open(pgn, key, owner, dao.analysisFor(key))
    }

    fun importText(text: String, source: String, open: OpenGame) {
        viewModelScope.launch { save(text, source, Sources.textKey(text), open) }
    }

    fun importFile(uri: Uri, open: OpenGame) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readCapped(MAX_FILE_BYTES + 1) }
                }.getOrNull()
            }
            when {
                bytes == null -> error = LoadError.Unreadable
                bytes.size > MAX_FILE_BYTES -> error = LoadError.TooLarge
                else -> importText(bytes.toString(Charsets.UTF_8), Sources.FILE, open)
            }
        }
    }

    fun importRemote(game: RemoteGame, open: OpenGame) {
        val source = if (game.key.startsWith("lichess:")) Sources.LICHESS else Sources.CHESS_COM
        viewModelScope.launch { save(game.pgn, source, game.key, open, searched) }
    }

    fun open(id: Long, open: OpenGame) {
        viewModelScope.launch { dao.get(id)?.let { open(it.pgn, it.sourceKey, it.owner, it.analysis) } }
    }

    /** Saves a game's analysis and drops the oldest ones past [KEEP_ANALYSES]. */
    fun saveAnalysis(key: String, text: String) = viewModelScope.launch {
        dao.setAnalysis(key, text, System.currentTimeMillis())
        dao.pruneAnalyses(KEEP_ANALYSES)
    }

    fun saveAccuracy(key: String, white: Double?, black: Double?) = viewModelScope.launch { dao.setAccuracy(key, white, black) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun deleteAll() = viewModelScope.launch { dao.deleteAll() }

    /** Loads the first page of [name]'s games. Games opened from them are saved with [name] as owner. */
    fun fetch(source: String, name: String) {
        cancelFetch()
        online = OnlineState.Loading
        fetchJob = viewModelScope.launch {
            online = try {
                val p = if (source == Sources.LICHESS) Lichess.Pager(name) else ChessCom.Pager(name)
                pager = p
                val games = p.next()
                searched = name.trim()
                // Saved games this user played, imported before owners were kept, are theirs too.
                dao.claimOwner(name.trim())
                OnlineState.Found(games, p.done)
            } catch (e: FetchError) {
                OnlineState.Failed(e)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /** Appends the next page; called when the list is scrolled to its end. */
    fun loadMore() {
        val s = online as? OnlineState.Found ?: return
        val p = pager ?: return
        if (s.done || s.loading) return
        online = s.copy(loading = true, moreError = null)
        fetchJob = viewModelScope.launch {
            online = try {
                val more = p.next()
                // A game can show up again if it is played while paging; keep keys unique.
                val seen = s.games.mapTo(HashSet()) { it.key }
                s.copy(games = s.games + more.filter { it.key !in seen }, done = p.done, loading = false)
            } catch (e: FetchError) {
                s.copy(loading = false, moreError = e)
            }
        }
    }

    fun cancelFetch() {
        fetchJob?.cancel()
        fetchJob = null
        when (val s = online) {
            OnlineState.Loading -> online = OnlineState.Idle
            is OnlineState.Found -> if (s.loading) online = s.copy(loading = false)
            else -> {}
        }
    }

    fun clearOnline() {
        cancelFetch()
        online = OnlineState.Idle
    }

    companion object {
        // ponytail: whole file in memory, capped. Stream games if big databases are ever needed.
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
        const val KEEP_ANALYSES = 100
    }
}

private fun java.io.InputStream.readCapped(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
