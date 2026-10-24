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
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.Setup
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
    /** The ply shown while looking back over the game; null shows the live position. */
    var viewPly by mutableStateOf<Int?>(null)
        private set
    /** Shows the best move arrow for the shown position. */
    var showBest by mutableStateOf(false)
        private set
    /** Full-strength best move for a position, as (FEN, UCI move). */
    var hint by mutableStateOf<Pair<String, String>?>(null)
        private set
    /** A best-move search is running; the engine's own move waits for it. */
    var hinting by mutableStateOf(false)
        private set

    // Follows the game move by move, so draws by repetition are seen.
    private val board = Board()
    private var exe: File? = null
    private var opts = EngineOptions()
    private var sf: StockfishSession? = null
    private var job: Job? = null
    /** Changes on each new game so a late engine reply is dropped. */
    private var gameId = 0

    /** The position games start from: the normal start, or one from Set up board. */
    var startFen by mutableStateOf(GameLine.START_FEN)
        private set

    val engineTurn: Boolean
        get() = line != null && result == null && ((line!!.positions.last().fen.split(' ')[1] == "w") != userWhite)

    /** Clears any game and makes [fen] the start of the next one. */
    fun prepare(fen: String) {
        reset()
        startFen = fen
    }

    fun start(exe: File, opts: EngineOptions, white: Boolean, elo: Int) {
        close()
        this.exe = exe
        this.opts = opts
        userWhite = white
        this.elo = elo
        board.loadFromFen(startFen)
        // Built through the PGN reader so a check in the start position is marked.
        line = GameLine.from(Pgn.parse(Setup.pgn(startFen)))
        resigned = false
        error = null
        viewPly = null
        // A set-up position can already be over.
        result = when {
            board.isMated -> if (board.sideToMove == Side.WHITE) "0-1" else "1-0"
            board.isDraw -> "1/2-1/2"
            else -> null
        }
    }

    /** The user's move. */
    fun play(uci: String) {
        if (line == null || engineTurn || result != null) return
        apply(uci)
    }

    private fun apply(uci: String) {
        val l = line ?: return
        line = l.play(l.plies.size, uci) ?: return
        viewPly = null
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
        if (!engineTurn || thinking || hinting) return
        val id = gameId
        val moves = line!!.plies.joinToString(" ") { it.uci }
        thinking = true
        error = null
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = sf ?: StockfishSession(exe).also { sf = it; setUp(it) }
                s.send("position fen $startFen" + if (moves.isEmpty()) "" else " moves $moves")
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

    /** FEN of the position on screen: the live one, or the one looked back at. */
    val shownFen: String
        get() = line?.let { it.positions[viewPly ?: it.plies.size].fen } ?: GameLine.START_FEN

    fun back() {
        val l = line ?: return
        viewPly = ((viewPly ?: l.plies.size) - 1).coerceAtLeast(0)
    }

    fun forward() {
        val l = line ?: return
        val next = (viewPly ?: return) + 1
        viewPly = if (next >= l.plies.size) null else next
    }

    fun toggleBest() {
        showBest = !showBest
    }

    /** Finds the best move for [fen] at full strength, between the engine's own moves. */
    fun findBest(fen: String) {
        val exe = exe ?: return
        if (!showBest || hint?.first == fen || thinking || hinting || engineTurn) return
        val id = gameId
        hinting = true
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = sf ?: StockfishSession(exe).also { sf = it; setUp(it) }
                s.send("setoption name UCI_LimitStrength value false")
                s.send("position fen $fen")
                s.send("go movetime $MOVE_MS")
                val best = Uci.parseBestMove(s.readUntil(SEARCH_TIMEOUT_MS) { it.startsWith("bestmove") }.last())
                s.send("setoption name UCI_LimitStrength value true")
                withContext(Dispatchers.Main) {
                    if (id != gameId) return@withContext
                    hinting = false
                    best?.let { hint = fen to it }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    if (id == gameId) {
                        hinting = false
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

    /** Plies [undo] takes back: the user's move while the engine is to move, else also the engine's reply. */
    private val undoPlies: Int
        get() = if (engineTurn) 1 else 2

    val canUndo: Boolean
        get() = line != null && result == null && line!!.plies.size >= undoPlies

    /** Takes back the user's last move (and the engine's reply), so it is the user's turn again. */
    fun undo() {
        val l = line ?: return
        if (!canUndo) return
        // Drops an engine reply still being searched.
        if (thinking) close()
        val keep = l.plies.size - undoPlies
        viewPly = null
        line = GameLine(l.plies.take(keep), l.positions.take(keep + 1))
        // Replay from the start so the board keeps the history for repetition draws.
        board.loadFromFen(startFen)
        for (p in line!!.plies) board.doMove(Move(p.uci, board.sideToMove))
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
        // Move numbers come from each position, so games from a set-up position number right too.
        val moves = l.plies.mapIndexed { i, p ->
            val (side, number) = l.positions[i].fen.split(' ').let { it[1] to it[5] }
            when {
                side == "w" -> "$number. ${p.san}"
                i == 0 -> "$number... ${p.san}"
                else -> p.san
            }
        }.joinToString(" ")
        return buildString {
            val date = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.ROOT).format(java.util.Date())
            append("[Event \"Rocket Chess vs engine\"]\n[Date \"$date\"]\n[White \"$white\"]\n[Black \"$black\"]\n[Result \"$r\"]\n")
            if (startFen != GameLine.START_FEN) append("[SetUp \"1\"]\n[FEN \"$startFen\"]\n")
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
        viewPly = null
    }

    /** Stops the engine and frees it; a pending engine move is asked again by [engineMove]. */
    fun close() {
        gameId++
        job?.cancel()
        job = null
        thinking = false
        hinting = false
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
