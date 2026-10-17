package com.uncosens.rocketchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.PgnException
import com.uncosens.rocketchess.chess.PgnGame
import com.uncosens.rocketchess.analysis.AnalysisSettings
import com.uncosens.rocketchess.analysis.Analyzer
import com.uncosens.rocketchess.analysis.PositionEval
import com.uncosens.rocketchess.engine.StockfishSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val done: Int, val total: Int) : AnalysisState
    data class Stopped(val done: Int, val total: Int) : AnalysisState
    data class Done(val settings: AnalysisSettings) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

sealed interface LoadError {
    data class Parse(val e: PgnException) : LoadError
    data object TooLarge : LoadError
    data object Unreadable : LoadError
}

class GameViewModel : ViewModel() {
    /** Raw PGN text as imported. Kept so it can be re-parsed or saved later. */
    var raw by mutableStateOf<String?>(null)
        private set
    var game by mutableStateOf<PgnGame?>(null)
        private set
    var line by mutableStateOf<GameLine?>(null)
        private set
    /** Index into line.positions: 0 is the start, N is after N plies. */
    var ply by mutableStateOf(0)
        private set
    var error by mutableStateOf<LoadError?>(null)
        private set
    /** Engine result per position; null until analyzed. Not saved: recomputed when needed. */
    var evals by mutableStateOf<List<PositionEval?>>(emptyList())
        private set
    var analysis by mutableStateOf<AnalysisState>(AnalysisState.Idle)
        private set
    private var job: Job? = null
    /** Identifies the current run so a cancelled run can never write its results. */
    private var runId = 0

    fun load(text: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { Pgn.parse(text).let { it to GameLine.from(it) } }
            }
            result.onSuccess { (g, l) ->
                stopAnalysis()
                raw = text; game = g; line = l; ply = 0; error = null
                evals = emptyList(); analysis = AnalysisState.Idle
            }.onFailure {
                error = if (it is PgnException) LoadError.Parse(it) else LoadError.Unreadable
            }
        }
    }

    fun analyze(exe: File) {
        val l = line ?: return
        stopAnalysis()
        val id = ++runId
        val total = l.positions.size
        evals = List(total) { null }
        analysis = AnalysisState.Running(0, total)
        job = viewModelScope.launch(Dispatchers.IO) {
            val sf = runCatching { StockfishSession(exe) }.getOrElse {
                withContext(Dispatchers.Main) { if (id == runId) analysis = AnalysisState.Failed(it.message ?: "start failed") }
                return@launch
            }
            try {
                val analyzer = Analyzer(sf)
                val settings = analyzer.start()
                l.positions.forEachIndexed { i, p ->
                    ensureActive()
                    val e = analyzer.analyze(p.fen, settings.depth)
                    withContext(Dispatchers.Main) {
                        if (id != runId) return@withContext
                        evals = evals.toMutableList().also { it[i] = e }
                        analysis = AnalysisState.Running(i + 1, total)
                    }
                }
                withContext(Dispatchers.Main) { if (id == runId) analysis = AnalysisState.Done(settings) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) { if (id == runId) analysis = AnalysisState.Failed(e.message ?: e.toString()) }
            } finally {
                sf.close()
            }
        }
    }

    /** Stops a running analysis and keeps the results so far. Also called when the app goes to the background. */
    fun stopAnalysis() {
        job?.cancel()
        job = null
        runId++
        (analysis as? AnalysisState.Running)?.let { analysis = AnalysisState.Stopped(it.done, it.total) }
    }

    fun grade(plyIndex: Int) = line?.let { Analyzer.grade(it, evals, plyIndex) }

    fun goTo(index: Int) {
        val last = line?.plies?.size ?: return
        ply = index.coerceIn(0, last)
    }
}
