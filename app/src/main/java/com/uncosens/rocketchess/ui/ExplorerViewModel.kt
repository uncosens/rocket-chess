package com.uncosens.rocketchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.Setup
import com.uncosens.rocketchess.engine.StockfishSession
import com.uncosens.rocketchess.engine.Uci
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A line of moves from the start position, walked in the opening explorer. */
class ExplorerViewModel : ViewModel() {
    var line by mutableStateOf(GameLine.from(Pgn.parse(Setup.pgn(GameLine.START_FEN))))
        private set
    /** Index into line.positions. Moves after it stay, so Next can go forward again. */
    var ply by mutableStateOf(0)
        private set
    var flipped by mutableStateOf(false)
    /** Shows the engine's best move arrow for the shown position. */
    var showBest by mutableStateOf(false)
        private set
    /** The engine's best move for a position, as (FEN, UCI move). */
    var hint by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var sf: StockfishSession? = null
    /** The position the arrow is wanted for; a search runs one at a time and then catches up. */
    private var wanted: String? = null
    private var searching = false

    val fen: String get() = line.positions[ply].fen

    /** Plays [uci] from the shown position. The same move as the line's next one keeps the rest of the line. */
    fun play(uci: String) {
        if (line.plies.getOrNull(ply)?.uci != uci) line = line.play(ply, uci) ?: return
        ply++
    }

    fun goTo(index: Int) {
        ply = index.coerceIn(0, line.plies.size)
    }

    /** The moves up to the shown position as a PGN, to open for analysis. */
    fun pgn(): String {
        val moves = line.plies.take(ply).mapIndexed { i, p -> if (i % 2 == 0) "${i / 2 + 1}. ${p.san}" else p.san }
        return "[Event \"Opening explorer\"]\n[White \"White\"]\n[Black \"Black\"]\n[Result \"*\"]\n\n${(moves + "*").joinToString(" ")}\n"
    }

    fun toggleBest() {
        showBest = !showBest
    }

    /** Finds the engine's best move for [fen]. Starts the engine on first use. */
    fun findBest(exe: File, opts: EngineOptions, fen: String) {
        wanted = fen
        if (!showBest || hint?.first == fen || searching) return
        searching = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val target = withContext(Dispatchers.Main) { wanted.takeIf { showBest && hint?.first != it } } ?: break
                    val s = sf ?: StockfishSession(exe).also { sf = it; setUp(it, opts) }
                    s.send("position fen $target")
                    s.send("go movetime $SEARCH_MS")
                    val best = Uci.parseBestMove(s.readUntil(TIMEOUT_MS) { it.startsWith("bestmove") }.last())
                    withContext(Dispatchers.Main) { if (best != null) hint = target to best }
                    if (best == null) break
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                withContext(Dispatchers.Main) { searching = false }
            }
        }
    }

    private fun setUp(s: StockfishSession, opts: EngineOptions) {
        s.send("uci")
        s.readUntil(TIMEOUT_MS) { it == "uciok" }
        s.send("setoption name Threads value ${opts.threads}")
        s.send("setoption name Hash value ${opts.hashMb}")
        s.send("isready")
        s.readUntil(TIMEOUT_MS) { it == "readyok" }
    }

    /** Frees the engine when leaving the explorer. */
    fun stop() {
        sf?.let { s -> thread { s.close() } }
        sf = null
    }

    override fun onCleared() = stop()

    private companion object {
        const val SEARCH_MS = 1000
        const val TIMEOUT_MS = 30_000L
    }
}
