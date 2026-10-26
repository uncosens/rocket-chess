package com.uncosens.rocketchess.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.analysis.PlayerReport
import com.uncosens.rocketchess.analysis.Report
import com.uncosens.rocketchess.analysis.ReportCache
import com.uncosens.rocketchess.analysis.ReportEntry
import com.uncosens.rocketchess.analysis.ReportMove
import com.uncosens.rocketchess.analysis.ReportPolicy
import com.uncosens.rocketchess.analysis.SavedAnalysis
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.data.AnalyzedHead
import com.uncosens.rocketchess.data.AppDatabase
import com.uncosens.rocketchess.data.KnownUsers
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.userSide
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportState {
    data object Loading : ReportState
    /** No My ID is set in Settings. */
    data object NoIds : ReportState
    data class NotEnough(val games: Int) : ReportState
    data class Ready(val report: Report) : ReportState
}

/** My Report from the user's most recently analyzed games. Each game's part is cached; only new or re-analyzed games are worked out. */
class ReportViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).games()
    private val file = File(app.filesDir, "report_cache.tsv")

    var state by mutableStateOf<ReportState>(ReportState.Loading)
        private set

    fun load() = viewModelScope.launch {
        state = ReportState.Loading
        val c = getApplication<Application>()
        val ids = listOf(Sources.CHESS_COM, Sources.LICHESS).map { KnownUsers.myId(c, it) }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) {
            state = ReportState.NoIds
            return@launch
        }
        state = withContext(Dispatchers.Default) {
            val heads = dao.analyzedHeads()
            val cache = (memory ?: runCatching { ReportCache.decode(file.readText()) }.getOrDefault(emptyMap())).toMutableMap()
            var changed = false
            val seen = mutableSetOf<String>()
            val games = mutableListOf<List<ReportMove>>()
            // Newest analysis first, until the report has its games.
            for (h in heads) {
                if (games.size >= ReportPolicy.RECENT_GAMES) break
                // Engine games are left out: the engine does not err like a person.
                if (h.source == Sources.ENGINE) continue
                val owner = ids.firstOrNull { it.equals(h.owner, ignoreCase = true) } ?: continue
                val at = h.analyzedAt ?: 0L
                val entry = cache[h.sourceKey]?.takeIf { it.analyzedAt == at && it.owner == owner }
                    ?: (entry(h, owner, at) ?: continue).also { cache[h.sourceKey] = it; changed = true }
                if (!entry.usable) continue
                // The same game saved twice (say pasted and searched) counts once.
                if (!seen.add(entry.sameGame)) continue
                games += entry.moves
            }
            // Deleted games leave the cache.
            val keys = heads.mapTo(HashSet()) { it.sourceKey }
            if (cache.keys.retainAll(keys)) changed = true
            memory = cache
            if (changed) runCatching { file.writeText(ReportCache.encode(cache)) }
            PlayerReport.build(games)?.let { ReportState.Ready(it) } ?: ReportState.NotEnough(games.size)
        }
    }

    /** Works out one game's part of the report from its saved analysis. Null if the row is gone. */
    private suspend fun entry(h: AnalyzedHead, owner: String, at: Long): ReportEntry? {
        val row = dao.pgnAndAnalysis(h.sourceKey) ?: return null
        val skip = ReportEntry(at, owner, false, "", emptyList())
        val white = userSide(h.white, h.black, owner) ?: return skip
        val (settings, evals) = SavedAnalysis.decode(row.analysis) ?: return skip
        if (settings.depth < ReportPolicy.MIN_DEPTH) return skip
        val line = runCatching { GameLine.from(Pgn.parse(row.pgn)) }.getOrNull() ?: return skip
        if (evals.size != line.positions.size || evals.count { it != null } < ReportPolicy.MIN_COVERAGE * evals.size) return skip
        val same = "${h.white.lowercase()}|${h.black.lowercase()}|${line.plies.joinToString(" ") { it.uci }}"
        return ReportEntry(at, owner, true, same, PlayerReport.moves(line, evals, white))
    }

    private companion object {
        /** The cache as last read or written, so a second open skips the file. */
        @Volatile var memory: Map<String, ReportEntry>? = null
    }
}
