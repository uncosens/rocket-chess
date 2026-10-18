package com.uncosens.rocketchess.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.PgnException
import com.uncosens.rocketchess.chess.PgnGame
import com.uncosens.rocketchess.data.userSide
import com.uncosens.rocketchess.analysis.AnalysisSettings
import com.uncosens.rocketchess.analysis.Analyzer
import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.analysis.PositionEval
import com.uncosens.rocketchess.engine.StockfishSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

/** Moves the user played on the board. [line] starts like the game; [branch] = plies it shares with the game. */
data class Variation(val line: GameLine, val branch: Int) {
    companion object {
        /** [next] as a variation of [main], or null when it only follows the game's moves. */
        fun of(main: GameLine, next: GameLine): Variation? {
            val shared = next.plies.zip(main.plies).takeWhile { (a, b) -> a.uci == b.uci }.size
            return if (shared == next.plies.size) null else Variation(next, shared)
        }
    }
}

class GameViewModel : ViewModel() {
    /** Raw PGN text as imported. Kept so it can be re-parsed or saved later. */
    var raw by mutableStateOf<String?>(null)
        private set
    var game by mutableStateOf<PgnGame?>(null)
        private set
    /** The saved game's source key, used to store its accuracy. */
    var key by mutableStateOf<String?>(null)
        private set
    /** The game as imported. */
    private var mainLine by mutableStateOf<GameLine?>(null)
    var variation by mutableStateOf<Variation?>(null)
        private set
    /** The line on screen: the user's variation, or the game. */
    val line: GameLine? get() = variation?.line ?: mainLine
    /** Index into line.positions: 0 is the start, N is after N plies. */
    var ply by mutableStateOf(0)
        private set
    /** Board seen from Black's side. Kept across games. */
    var flipped by mutableStateOf(false)
    var error by mutableStateOf<LoadError?>(null)
        private set
    /** Engine result per game position; null until analyzed. Not saved: recomputed when needed. */
    private var mainEvals by mutableStateOf<List<PositionEval?>>(emptyList())
    /** Engine results for positions the user reached on the board, by FEN. */
    private var liveEvals by mutableStateOf<Map<String, PositionEval>>(emptyMap())
    /** Engine result per position of [line]. */
    val evals: List<PositionEval?> by derivedStateOf {
        val v = variation ?: return@derivedStateOf mainEvals
        v.line.positions.mapIndexed { i, p -> (if (i <= v.branch) mainEvals.getOrNull(i) else null) ?: liveEvals[p.fen] }
    }
    var analysis by mutableStateOf<AnalysisState>(AnalysisState.Idle)
        private set
    var liveError by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    /** Identifies the current run so a cancelled run can never write its results. */
    private var runId = 0
    private var liveJob: Job? = null
    /** Latest positions to analyze live. Older requests are dropped. */
    private val liveQueue = Channel<List<String>>(Channel.CONFLATED)

    /** Loads a game. If a [names] entry played it, the board shows that side at the bottom. */
    fun load(text: String, gameKey: String, names: Set<String>) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { Pgn.parse(text).let { it to GameLine.from(it) } }
            }
            result.onSuccess { (g, l) ->
                stopAnalysis()
                raw = text; game = g; key = gameKey; mainLine = l; variation = null; ply = 0; error = null
                userSide(g.tags["White"] ?: "", g.tags["Black"] ?: "", names)?.let { flipped = !it }
                mainEvals = emptyList(); liveEvals = emptyMap(); analysis = AnalysisState.Idle
            }.onFailure {
                error = if (it is PgnException) LoadError.Parse(it) else LoadError.Unreadable
            }
        }
    }

    /** Analyzes the game. After a stop it goes on from where it stopped. */
    fun analyze(exe: File, opts: EngineOptions) {
        val l = mainLine ?: return
        stopAnalysis()
        val id = ++runId
        val total = l.positions.size
        if (mainEvals.size != total) mainEvals = List(total) { null }
        analysis = AnalysisState.Running(mainEvals.count { it != null }, total)
        job = viewModelScope.launch(Dispatchers.IO) {
            val sf = runCatching { StockfishSession(exe) }.getOrElse {
                withContext(Dispatchers.Main) { if (id == runId) analysis = AnalysisState.Failed(it.message ?: "start failed") }
                return@launch
            }
            try {
                val analyzer = Analyzer(sf)
                val settings = analyzer.start(opts)
                l.positions.forEachIndexed { i, p ->
                    ensureActive()
                    if (mainEvals[i] != null) return@forEachIndexed
                    val e = analyzer.analyze(p.fen, settings.depth)
                    withContext(Dispatchers.Main) {
                        if (id != runId) return@withContext
                        mainEvals = mainEvals.toMutableList().also { it[i] = e }
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
        liveJob?.cancel()
        liveJob = null
    }

    /** Plays [uci] from the shown position. Back on the game's own moves, the variation ends. */
    fun play(uci: String) {
        val main = mainLine ?: return
        val next = line?.play(ply, uci) ?: return
        variation = Variation.of(main, next)
        ply++
    }

    /** Goes back to the game, at the position where the variation left it. */
    fun leaveVariation() {
        val v = variation ?: return
        ply = minOf(ply, v.branch)
        variation = null
    }

    /**
     * Analyzes the shown variation position and the one before it, so the move gets a grade.
     * One engine stays open for this until [stopAnalysis].
     */
    fun analyzeLive(exe: File, opts: EngineOptions) {
        val l = variation?.line ?: return
        val fens = listOf(ply - 1, ply).filter { it >= 0 && evals.getOrNull(it) == null }.map { l.positions[it].fen }
        if (fens.isEmpty()) return
        liveQueue.trySend(fens)
        if (liveJob?.isActive == true) return
        liveError = null
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            val sf = runCatching { StockfishSession(exe) }.getOrElse {
                withContext(Dispatchers.Main) { liveError = it.message ?: "start failed" }
                return@launch
            }
            try {
                val analyzer = Analyzer(sf)
                val settings = analyzer.start(opts)
                for (batch in liveQueue) for (fen in batch) {
                    if (fen in liveEvals) continue
                    val e = analyzer.analyze(fen, settings.depth)
                    withContext(Dispatchers.Main) { liveEvals = liveEvals + (fen to e) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) { liveError = e.message ?: e.toString() }
            } finally {
                sf.close()
            }
        }
    }

    fun grade(plyIndex: Int) = line?.let { Analyzer.grade(it, evals, plyIndex) }

    /** Mean move accuracy of the game for White or Black. */
    fun accuracy(white: Boolean) = mainLine?.let { Analyzer.accuracy(it, mainEvals, white) }

    fun goTo(index: Int) {
        val last = line?.plies?.size ?: return
        ply = index.coerceIn(0, last)
    }
}
