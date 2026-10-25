package com.uncosens.rocketchess.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.analysis.Mistake
import com.uncosens.rocketchess.analysis.Mistakes
import com.uncosens.rocketchess.analysis.Puzzle
import com.uncosens.rocketchess.analysis.Puzzles
import com.uncosens.rocketchess.analysis.SavedAnalysis
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.data.AppDatabase
import com.uncosens.rocketchess.data.KnownUsers
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.userSide
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A mistake with the game it came from. */
data class MistakeNote(val mistake: Mistake, val userWhite: Boolean, val white: String, val black: String, val date: String)

/**
 * One puzzle to solve: a mistake of the user's or a Lichess puzzle. [solution] is the user's move,
 * the opponent's reply, and so on; [firstAnswers] are all first moves that count.
 */
data class Drill(
    val key: String,
    val fen: String,
    val lastMove: Pair<String, String>?,
    val userWhite: Boolean,
    val solution: List<String>,
    val firstAnswers: List<String>,
    val note: MistakeNote? = null,
    val puzzle: Puzzle? = null,
)

enum class Attempt { Solving, Wrong, Going, Solved, Shown }

/** Mistake Notes: the user's Mistakes and Blunders and Lichess puzzles of the same kinds, mixed. */
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).games()
    private val prefs = app.getSharedPreferences("notes", Context.MODE_PRIVATE)

    /** Null while loading. */
    var drills by mutableStateOf<List<Drill>?>(null)
        private set
    var index by mutableStateOf(0)
        private set
    var attempt by mutableStateOf(Attempt.Solving)
        private set
    /** The shown position and the move that led to it. */
    var fen by mutableStateOf("")
        private set
    var lastMove by mutableStateOf<Pair<String, String>?>(null)
        private set
    var check by mutableStateOf<String?>(null)
        private set
    /** True when the last move shown is the user's right move, drawn with the Best badge. */
    var right by mutableStateOf(false)
        private set
    /** Index into the drill's solution of the user's next move. */
    private var step = 0
    /** True while the opponent's reply is about to be played. */
    var replying by mutableStateOf(false)
        private set
    /** The wrong move just tried, as SAN. */
    var tried by mutableStateOf<String?>(null)
        private set
    /** Counts wrong tries, so the board drops its selection after each. */
    var misses by mutableStateOf(0)
        private set
    /** The answer to draw as an arrow once shown. */
    var arrow by mutableStateOf<String?>(null)
        private set
    /** True when no My ID is set, so only games against the engine count. */
    var noIds by mutableStateOf(false)
        private set
    /** The rating puzzles are picked near: the user's recent game rating. */
    var rating by mutableStateOf(DEFAULT_RATING)
        private set
    var solved by mutableStateOf(emptySet<String>())
        private set

    val drill: Drill? get() = drills?.getOrNull(index)

    fun load() = viewModelScope.launch {
        drills = null
        val c = getApplication<Application>()
        val ids = listOf(Sources.CHESS_COM, Sources.LICHESS).map { KnownUsers.myId(c, it) }.filter { it.isNotEmpty() }
        noIds = ids.isEmpty()
        solved = prefs.getStringSet(SOLVED, null).orEmpty().toSet()
        val rows = dao.analyzed()
        drills = withContext(Dispatchers.Default) {
            val all = puzzles ?: Puzzles.parse(c.assets.open("puzzles/puzzles.tsv").bufferedReader().use { it.readText() }).also { puzzles = it }
            // Ratings from the newest games first.
            val ratings = mutableListOf<Int>()
            val notes = rows.flatMap { g ->
                // Games against the engine have "You" as owner.
                val owner = if (g.source == Sources.ENGINE) g.owner else ids.firstOrNull { it.equals(g.owner, ignoreCase = true) }
                val white = userSide(g.white, g.black, owner) ?: return@flatMap emptyList()
                val evals = SavedAnalysis.decode(g.analysis)?.second ?: return@flatMap emptyList()
                val pgn = runCatching { Pgn.parse(g.pgn) }.getOrNull() ?: return@flatMap emptyList()
                pgn.tags[if (white) "WhiteElo" else "BlackElo"]?.toIntOrNull()?.let { ratings += it }
                val line = runCatching { GameLine.from(pgn) }.getOrNull() ?: return@flatMap emptyList()
                if (evals.size != line.positions.size) return@flatMap emptyList()
                Mistakes.of(line, evals, white).map { MistakeNote(it, white, g.white, g.black, g.date) }
            }.distinctBy { it.mistake.key }
            val r = ratings.take(RATING_GAMES).sorted().let { if (it.isEmpty()) DEFAULT_RATING else it[it.size / 2] }
            withContext(Dispatchers.Main) { rating = r }
            // One unsolved puzzle near the user's rating per mistake, of one of its kinds picked at random.
            val random = Random(System.currentTimeMillis())
            val used = solved.filter { it.startsWith(PUZZLE) }.map { it.removePrefix(PUZZLE) }.toMutableSet()
            fun next(themes: List<String>) = Puzzles.pick(all, r, themes.randomOrNull(random), used, random)?.also { used += it.id }?.let(::drillOf)
            if (notes.isEmpty()) List(ALONE) { next(emptyList()) }.filterNotNull()
            else (notes.map(::drillOf) + notes.mapNotNull { next(it.mistake.themes) }).shuffled(random)
        }
        index = 0
        reset()
    }

    /** Checks the user's move. A right move that is not the last brings the opponent's reply. */
    fun play(uci: String) {
        val d = drill ?: return
        val after = GameLine.after(fen, uci) ?: return
        val last = step == d.solution.size - 1
        // As on Lichess, any mate in the last move counts.
        val ok = uci == d.solution[step] || (step == 0 && uci in d.firstAnswers) || (last && after.mate)
        if (!ok) {
            attempt = Attempt.Wrong
            tried = GameLine.san(fen, uci)
            misses++
            // Show the wrong move for a moment, then go back.
            val back = Triple(fen, lastMove, check)
            val wasRight = right
            show(uci, after.fen, after.check)
            right = false
            replying = true
            val at = index
            viewModelScope.launch {
                delay(REPLY_MS)
                if (index != at || attempt != Attempt.Wrong) return@launch
                fen = back.first
                lastMove = back.second
                check = back.third
                right = wasRight
                replying = false
            }
            return
        }
        show(uci, after.fen, after.check)
        right = true
        step++
        tried = null
        if (step >= d.solution.size || after.mate) {
            attempt = Attempt.Solved
            solved = solved + d.key
            prefs.edit().putStringSet(SOLVED, solved).apply()
            return
        }
        attempt = Attempt.Going
        replying = true
        val at = index
        viewModelScope.launch {
            delay(REPLY_MS)
            // Next was pressed meanwhile.
            if (index != at || attempt == Attempt.Solving) return@launch
            val reply = d.solution[step]
            GameLine.after(fen, reply)?.let { show(reply, it.fen, it.check) }
            right = false
            step++
            replying = false
        }
    }

    fun showAnswer() {
        val d = drill ?: return
        attempt = Attempt.Shown
        arrow = d.solution.getOrNull(step)
    }

    fun next() {
        val size = drills?.size ?: return
        if (size == 0) return
        // After the last one, the same puzzles again in a new order.
        if (index + 1 == size) drills = drills?.shuffled()
        index = (index + 1) % size
        reset()
    }

    private fun show(uci: String, next: String, inCheck: String?) {
        fen = next
        lastMove = uci.substring(0, 2) to uci.substring(2, 4)
        check = inCheck
    }

    private fun reset() {
        val d = drill
        fen = d?.fen.orEmpty()
        lastMove = d?.lastMove
        check = null
        right = false
        step = 0
        attempt = Attempt.Solving
        replying = false
        tried = null
        arrow = null
    }

    private fun drillOf(n: MistakeNote) = Drill(n.mistake.key, n.mistake.fen, n.mistake.lastMove, n.userWhite, listOf(n.mistake.answers.first()), n.mistake.answers, note = n)

    /** The puzzle starts after the opponent's first move. */
    private fun drillOf(p: Puzzle): Drill? {
        val start = GameLine.after(p.fen, p.moves[0]) ?: return null
        val solution = p.moves.drop(1)
        return Drill(PUZZLE + p.id, start.fen, p.moves[0].substring(0, 2) to p.moves[0].substring(2, 4), start.fen.split(' ')[1] == "w", solution, solution.take(1), puzzle = p)
    }

    companion object {
        private const val SOLVED = "solved"
        /** Key prefix of Lichess puzzles in the solved set. */
        private const val PUZZLE = "p:"
        const val DEFAULT_RATING = 1500
        /** The rating is the median of this many recent games. */
        private const val RATING_GAMES = 20
        /** Puzzles shown when there are no mistakes yet. */
        private const val ALONE = 30
        private const val REPLY_MS = 500L
        /** Loaded once; the set does not change while the app runs. */
        @Volatile private var puzzles: List<Puzzle>? = null
    }
}
