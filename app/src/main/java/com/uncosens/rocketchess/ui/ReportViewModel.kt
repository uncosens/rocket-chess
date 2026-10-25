package com.uncosens.rocketchess.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.analysis.PlayerReport
import com.uncosens.rocketchess.analysis.Report
import com.uncosens.rocketchess.analysis.ReportPolicy
import com.uncosens.rocketchess.analysis.SavedAnalysis
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.data.AppDatabase
import com.uncosens.rocketchess.data.KnownUsers
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.userSide
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

/** My Report from every analyzed game the user's My IDs played. */
class ReportViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).games()

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
        val rows = dao.analyzed()
        state = withContext(Dispatchers.Default) {
            val seen = mutableSetOf<String>()
            val games = rows.mapNotNull { g ->
                // Engine games are left out: the engine does not err like a person.
                if (g.source == Sources.ENGINE) return@mapNotNull null
                val owner = ids.firstOrNull { it.equals(g.owner, ignoreCase = true) } ?: return@mapNotNull null
                val white = userSide(g.white, g.black, owner) ?: return@mapNotNull null
                val (settings, evals) = SavedAnalysis.decode(g.analysis) ?: return@mapNotNull null
                if (settings.depth < ReportPolicy.MIN_DEPTH) return@mapNotNull null
                val line = runCatching { GameLine.from(Pgn.parse(g.pgn)) }.getOrNull() ?: return@mapNotNull null
                if (evals.size != line.positions.size || evals.count { it != null } < ReportPolicy.MIN_COVERAGE * evals.size) return@mapNotNull null
                // The same game saved twice (say pasted and searched) counts once.
                if (!seen.add("${g.white.lowercase()}|${g.black.lowercase()}|${line.plies.joinToString(" ") { it.uci }}")) return@mapNotNull null
                white to PlayerReport.moves(line, evals, white)
            }
            PlayerReport.build(games)?.let { ReportState.Ready(it) } ?: ReportState.NotEnough(games.size)
        }
    }
}
