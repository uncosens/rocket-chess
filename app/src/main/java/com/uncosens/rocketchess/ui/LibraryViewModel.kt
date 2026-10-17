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
import com.uncosens.rocketchess.data.SavedGame
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.saveOrFind
import com.uncosens.rocketchess.remote.ChessCom
import com.uncosens.rocketchess.remote.FetchError
import com.uncosens.rocketchess.remote.Lichess
import com.uncosens.rocketchess.remote.RemoteGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Found(val games: List<RemoteGame>) : OnlineState
    data class Failed(val error: FetchError) : OnlineState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).games()

    val games = dao.summaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<GameSummary>())

    var error by mutableStateOf<LoadError?>(null)
        private set
    var online by mutableStateOf<OnlineState>(OnlineState.Idle)
        private set
    private var fetchJob: Job? = null

    /** Checks the PGN, saves it (or finds the saved copy) and passes its text to [open]. */
    private suspend fun save(pgn: String, source: String, key: String, open: (String) -> Unit) {
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
            ),
        )
        error = null
        open(pgn)
    }

    fun importText(text: String, source: String, open: (String) -> Unit) {
        viewModelScope.launch { save(text, source, Sources.textKey(text), open) }
    }

    fun importFile(uri: Uri, open: (String) -> Unit) {
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

    fun importRemote(game: RemoteGame, open: (String) -> Unit) {
        val source = if (game.key.startsWith("lichess:")) Sources.LICHESS else Sources.CHESS_COM
        viewModelScope.launch { save(game.pgn, source, game.key, open) }
    }

    fun open(id: Long, open: (String) -> Unit) {
        viewModelScope.launch { dao.get(id)?.let { open(it.pgn) } }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun deleteAll() = viewModelScope.launch { dao.deleteAll() }

    fun fetch(source: String, name: String) {
        cancelFetch()
        online = OnlineState.Loading
        fetchJob = viewModelScope.launch {
            online = try {
                val games = if (source == Sources.LICHESS) Lichess.recentGames(name) else ChessCom.recentGames(name)
                OnlineState.Found(games)
            } catch (e: FetchError) {
                OnlineState.Failed(e)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun cancelFetch() {
        fetchJob?.cancel()
        fetchJob = null
        if (online == OnlineState.Loading) online = OnlineState.Idle
    }

    fun clearOnline() {
        cancelFetch()
        online = OnlineState.Idle
    }

    companion object {
        // ponytail: whole file in memory, capped. Stream games if big databases are ever needed.
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
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
