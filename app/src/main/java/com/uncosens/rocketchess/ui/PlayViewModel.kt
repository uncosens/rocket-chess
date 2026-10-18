package com.uncosens.rocketchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Position
import com.uncosens.rocketchess.engine.StockfishSession
import com.uncosens.rocketchess.engine.Uci
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A game against Stockfish from the start position, with the engine held to an Elo. */
class PlayViewModel : ViewModel() {
    /** The game so far; null before the first start. */
    var line by mutableStateOf<GameLine?>(null)
        private set
    var userWhite by mutableStateOf(true)
        private set
    var elo by mutableStateOf(DEFAULT_ELO)
        private set
    var thinking by mutableStateOf(false)
        private set
    /** "1-0", "0-1" or "1/2-1/2" once the game is over. */
    var result by mutableStateOf<String?>(null)
        private set
    var resigned by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Follows the game move by move, so draws by repetition are seen.
    private val board = Board()
    private var exe: File? = null
    private var opts = EngineOptions()
    private var sf: StockfishSession? = null
    private var job: Job? = null
    /** Changes on each new game so a late engine reply is dropped. */
    private var gameId = 0

    val engineTurn: Boolean
        get() = line != null && result == null && ((line!!.plies.size % 2 == 0) != userWhite)

    fun start(exe: File, opts: EngineOptions, white: Boolean, elo: Int) {
        close()
        this.exe = exe
        this.opts = opts
        userWhite = white
        this.elo = elo
        board.loadFromFen(GameLine.START_FEN)
        line = GameLine(emptyList(), listOf(Position(GameLine.START_FEN, null)))
        result = null
        resigned = false
        error = null
    }

    /** The user's move. */
    fun play(uci: String) {
        if (line == null || engineTurn || result != null) return
        apply(uci)
    }

    private fun apply(uci: String) {
        val l = line ?: return
        line = l.play(l.plies.size, uci) ?: return
        board.doMove(Move(uci, board.sideToMove))
        result = when {
            board.isMated -> if (board.sideToMove == Side.WHITE) "0-1" else "1-0"
            board.isDraw -> "1/2-1/2"
            else -> null
        }
        // The game is over: free the engine's memory now.
        if (result != null) close()
    }

    /** Asks the engine for its move when it is its turn. Starts the engine on first use. */
    fun engineMove() {
        val exe = exe ?: return
        if (!engineTurn || thinking) return
        val id = gameId
        val moves = line!!.plies.joinToString(" ") { it.uci }
        thinking = true
        error = null
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = sf ?: StockfishSession(exe).also { sf = it; setUp(it) }
                s.send("position startpos" + if (moves.isEmpty()) "" else " moves $moves")
                s.send("go movetime $MOVE_MS")
                val best = Uci.parseBestMove(s.readUntil(SEARCH_TIMEOUT_MS) { it.startsWith("bestmove") }.last())
                withContext(Dispatchers.Main) {
                    if (id != gameId) return@withContext
                    thinking = false
                    best?.let(::apply)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    if (id == gameId) {
                        thinking = false
                        error = e.message ?: e.toString()
                    }
                }
            }
        }
    }

    private fun setUp(s: StockfishSession) {
        s.send("uci")
        s.readUntil(TIMEOUT_MS) { it == "uciok" }
        s.send("setoption name Threads value ${opts.threads}")
        s.send("setoption name Hash value ${opts.hashMb}")
        s.send("setoption name UCI_LimitStrength value true")
        s.send("setoption name UCI_Elo value $elo")
        s.send("ucinewgame")
        s.send("isready")
        s.readUntil(TIMEOUT_MS) { it == "readyok" }
    }

    fun resign() {
        if (line == null || result != null) return
        close()
        resigned = true
        result = if (userWhite) "0-1" else "1-0"
    }

    /** The game as PGN, with [you] and [engine] as player names. */
    fun pgn(you: String, engine: String): String {
        val l = line ?: return ""
        val r = result ?: "*"
        val white = if (userWhite) you else engine
        val black = if (userWhite) engine else you
        val winner = if (r == "1-0") white else black
        val termination = when {
            r == "1/2-1/2" -> "Game drawn"
            resigned -> "$winner won by resignation"
            r != "*" -> "$winner won by checkmate"
            else -> null
        }
        val moves = l.plies.mapIndexed { i, p -> if (i % 2 == 0) "${i / 2 + 1}. ${p.san}" else p.san }.joinToString(" ")
        return buildString {
            append("[Event \"Rocket Chess vs engine\"]\n[White \"$white\"]\n[Black \"$black\"]\n[Result \"$r\"]\n")
            termination?.let { append("[Termination \"$it\"]\n") }
            append("\n$moves $r\n")
        }
    }

    /** Back to the start panel. */
    fun reset() {
        close()
        line = null
        result = null
        resigned = false
        error = null
    }

    /** Stops the engine and frees it; a pending engine move is asked again by [engineMove]. */
    fun close() {
        gameId++
        job?.cancel()
        job = null
        thinking = false
        sf?.let { s -> thread { s.close() } }
        sf = null
    }

    override fun onCleared() = close()

    companion object {
        const val DEFAULT_ELO = 1500
        val ELO_RANGE = 1400..2800
        private const val MOVE_MS = 500
        private const val TIMEOUT_MS = 10_000L
        private const val SEARCH_TIMEOUT_MS = 30_000L
    }
}
