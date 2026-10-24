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
import com.uncosens.rocketchess.analysis.EngineBudget
import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.analysis.PositionEval
import com.uncosens.rocketchess.analysis.SavedAnalysis
import com.uncosens.rocketchess.analysis.bookGrade
import com.uncosens.rocketchess.opening.Opening
import com.uncosens.rocketchess.opening.OpeningData
import com.uncosens.rocketchess.engine.StockfishSession
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Shallower results of positions still being searched live, shown until the full depth is done. */
    private var livePartial by mutableStateOf<Map<String, PositionEval>>(emptyMap())
    /** Engine result per position of [line]. */
    val evals: List<PositionEval?> by derivedStateOf {
        val v = variation ?: return@derivedStateOf mainEvals
        v.line.positions.mapIndexed { i, p -> (if (i <= v.branch) mainEvals.getOrNull(i) else null) ?: liveEvals[p.fen] ?: livePartial[p.fen] }
    }
    var analysis by mutableStateOf<AnalysisState>(AnalysisState.Idle)
        private set
    var liveError by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    /** Settings of the run that made [mainEvals], and whether those have new results not yet saved. */
    private var runSettings: AnalysisSettings? = null
    /** Engines of the running full analysis. */
    private val sessions = mutableListOf<StockfishSession>()
    private var unsaved = false
    /** Identifies the current run so a cancelled run can never write its results. */
    private var runId = 0
    private var liveJob: Job? = null
    /** Latest positions to analyze live. Older requests are dropped. */
    private val liveQueue = Channel<List<String>>(Channel.CONFLATED)
    /** Set when the live engine has gone idle and is quitting; a new request then starts another. */
    @Volatile private var liveQuitting = false
    /** True while the live engine searches; the full analysis then waits before its next position. */
    @Volatile private var liveSearching = false

    /**
     * Loads a game. If [owner] played it, the board shows that side at the bottom.
     * A [saved] analysis shows at once; a partial one goes on when analysis starts.
     */
    fun load(text: String, gameKey: String, owner: String?, saved: String? = null) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { Pgn.parse(text).let { it to GameLine.from(it) } }
            }
            result.onSuccess { (g, l) ->
                stopAnalysis()
                raw = text; game = g; key = gameKey; mainLine = l; variation = null; ply = 0; error = null
                userSide(g.tags["White"] ?: "", g.tags["Black"] ?: "", owner)?.let { flipped = !it }
                val restored = saved?.let(SavedAnalysis::decode)?.takeIf { it.second.size == l.positions.size }
                mainEvals = restored?.second ?: emptyList(); liveEvals = emptyMap(); runSettings = restored?.first; unsaved = false
                analysis = if (restored != null && restored.second.all { it != null }) AnalysisState.Done(restored.first) else AnalysisState.Idle
            }.onFailure {
                error = if (it is PgnException) LoadError.Parse(it) else LoadError.Unreadable
            }
        }
    }

    /**
     * Analyzes the game on [engines] engines at once, each on one thread taking the next position left.
     * After a stop it goes on from where it stopped; [fresh] starts over.
     */
    fun analyze(exe: File, opts: EngineOptions, engines: Int, fresh: Boolean = false) {
        val l = mainLine ?: return
        stopAnalysis()
        val id = ++runId
        val total = l.positions.size
        if (fresh || mainEvals.size != total) mainEvals = List(total) { null }
        analysis = AnalysisState.Running(mainEvals.count { it != null }, total)
        val todo = ConcurrentLinkedQueue(l.positions.indices.filter { mainEvals[it] == null })
        val each = opts.copy(threads = 1, hashMb = EngineBudget.hashShare(opts, engines))
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    repeat(engines) {
                        launch {
                            val sf = StockfishSession(exe).also { synchronized(sessions) { sessions += it } }
                            try {
                                val analyzer = Analyzer(sf)
                                val s = analyzer.start(each)
                                // One line for the whole run: all engines together, with the full hash.
                                val settings = s.copy(threads = engines, hashMb = opts.hashMb)
                                withContext(Dispatchers.Main) { if (id == runId) runSettings = settings }
                                while (true) {
                                    ensureActive()
                                    // The user's own move comes first: wait while it is searched.
                                    while (liveSearching) delay(LIVE_WAIT_MS)
                                    val i = todo.poll() ?: break
                                    val e = analyzer.analyze(l.positions[i].fen, s.depth)
                                    withContext(Dispatchers.Main) {
                                        if (id != runId) return@withContext
                                        mainEvals = mainEvals.toMutableList().also { it[i] = e }
                                        unsaved = true
                                        analysis = AnalysisState.Running(mainEvals.count { it != null }, total)
                                    }
                                }
                            } finally {
                                synchronized(sessions) { sessions -= sf }
                                sf.close()
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (id == runId) runSettings?.let { analysis = AnalysisState.Done(it) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) { if (id == runId) analysis = AnalysisState.Failed(e.message ?: e.toString()) }
            }
        }
    }

    /** Stops a running analysis and keeps the results so far. Also called when the app goes to the background. */
    fun stopAnalysis() {
        job?.cancel()
        job = null
        // Quit the engines now instead of waiting for their searches, so their memory is freed at once.
        val running = synchronized(sessions) { sessions.toList() }
        if (running.isNotEmpty()) thread { running.forEach { it.close() } }
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
        val v = variation ?: return
        val l = v.line
        // The shown position first, then the one before it (needed for the move's grade).
        val fens = listOf(ply, ply - 1).filter { it >= 0 }
            .filter { i -> (if (i <= v.branch) mainEvals.getOrNull(i) else null) == null && l.positions[i].fen !in liveEvals }
            .map { l.positions[it].fen }
        if (fens.isEmpty()) return
        liveQueue.trySend(fens)
        if (liveJob?.isActive == true && !liveQuitting) return
        liveError = null
        liveQuitting = false
        liveJob = viewModelScope.launch(Dispatchers.IO) {
            val sf = runCatching { StockfishSession(exe) }.getOrElse {
                withContext(Dispatchers.Main) { liveError = it.message ?: "start failed" }
                return@launch
            }
            try {
                val analyzer = Analyzer(sf)
                // One position at a time: more than two threads only slows a fixed-depth search on phones.
                val settings = analyzer.start(opts.copy(threads = minOf(opts.threads, 2)))
                // Quits after a short idle time, so the engine's memory is not held while the user only looks.
                while (true) {
                    val batch = withTimeoutOrNull(LIVE_IDLE_MS) { liveQueue.receive() }
                    if (batch == null) {
                        liveQuitting = true
                        break
                    }
                    for (fen in batch) {
                        if (fen in liveEvals) continue
                        liveSearching = true
                        val e = try {
                            // Each finished depth shows at once, so even a slow search gives an eval quickly.
                            analyzer.analyze(fen, settings.depth) { partial ->
                                viewModelScope.launch(Dispatchers.Main) { if (fen !in liveEvals) livePartial = livePartial + (fen to partial) }
                            }
                        } finally {
                            liveSearching = false
                        }
                        withContext(Dispatchers.Main) { liveEvals = liveEvals + (fen to e); livePartial = livePartial - fen }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) { liveError = e.message ?: e.toString() }
            } finally {
                sf.close()
            }
        }
    }

    /** Plies of [line], from its start, that follow the opening book. */
    private val bookPlies: Int by derivedStateOf {
        val l = line
        val book = OpeningData.book
        if (l == null || book == null) 0 else book.bookPlies(l)
    }

    fun grade(plyIndex: Int) = line?.let { Analyzer.grade(it, evals, plyIndex) }?.let { it.copy(cls = bookGrade(it.cls, plyIndex < bookPlies)) }

    /** The opening name of the shown position, or the deepest named one before it. */
    val opening: Opening? by derivedStateOf { line?.let { l -> OpeningData.names?.of(l, ply) } }

    /** The game's analysis as SavedAnalysis text if it has new results since the last save; marks it saved. */
    fun takeAnalysisToSave(): String? {
        val s = runSettings ?: return null
        if (!unsaved) return null
        unsaved = false
        return SavedAnalysis.encode(s, mainEvals)
    }

    /** Mean move accuracy of the game for White or Black. */
    fun accuracy(white: Boolean) = mainLine?.let { Analyzer.accuracy(it, mainEvals, white) }

    fun goTo(index: Int) {
        val last = line?.plies?.size ?: return
        ply = index.coerceIn(0, last)
    }

    private companion object {
        const val LIVE_IDLE_MS = 10_000L
        const val LIVE_WAIT_MS = 25L
    }
}
