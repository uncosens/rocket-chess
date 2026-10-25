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

/** A mistake with the game it came from. */
data class MistakeNote(val mistake: Mistake, val userWhite: Boolean, val white: String, val black: String, val date: String)

enum class Attempt { Solving, Wrong, Solved, Shown }

/** Mistake Notes: the user's Mistakes and Blunders from analyzed games, to solve again as puzzles. */
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).games()
    private val prefs = app.getSharedPreferences("notes", Context.MODE_PRIVATE)

    /** Null while loading. Unsolved first, newest analysis first. */
    var notes by mutableStateOf<List<MistakeNote>?>(null)
        private set
    var index by mutableStateOf(0)
        private set
    var attempt by mutableStateOf(Attempt.Solving)
        private set
    /** The wrong move just tried, as SAN. */
    var tried by mutableStateOf<String?>(null)
        private set
    /** Counts wrong tries, so the board drops its selection after each. */
    var misses by mutableStateOf(0)
        private set
    /** The answer to draw as an arrow once solved or shown. */
    var arrow by mutableStateOf<String?>(null)
        private set
    /** True when no My ID is set, so only games against the engine count. */
    var noIds by mutableStateOf(false)
        private set
    var solved by mutableStateOf(emptySet<String>())
        private set

    val note: MistakeNote? get() = notes?.getOrNull(index)

    fun load() = viewModelScope.launch {
        notes = null
        val c = getApplication<Application>()
        val ids = listOf(Sources.CHESS_COM, Sources.LICHESS).map { KnownUsers.myId(c, it) }.filter { it.isNotEmpty() }
        noIds = ids.isEmpty()
        solved = prefs.getStringSet(SOLVED, null).orEmpty().toSet()
        val rows = dao.analyzed()
        val all = withContext(Dispatchers.Default) {
            rows.flatMap { g ->
                // Games against the engine have "You" as owner.
                val owner = if (g.source == Sources.ENGINE) g.owner else ids.firstOrNull { it.equals(g.owner, ignoreCase = true) }
                val white = userSide(g.white, g.black, owner) ?: return@flatMap emptyList()
                val evals = SavedAnalysis.decode(g.analysis)?.second ?: return@flatMap emptyList()
                val line = runCatching { GameLine.from(Pgn.parse(g.pgn)) }.getOrNull() ?: return@flatMap emptyList()
                if (evals.size != line.positions.size) return@flatMap emptyList()
                Mistakes.of(line, evals, white).map { MistakeNote(it, white, g.white, g.black, g.date) }
            }.distinctBy { it.mistake.key }
        }
        notes = all.sortedBy { it.mistake.key in solved }
        index = 0
        attempt = Attempt.Solving
        tried = null
        arrow = null
    }

    /** Checks the user's move against the answers. */
    fun play(uci: String) {
        val n = note ?: return
        if (uci in n.mistake.answers) {
            attempt = Attempt.Solved
            tried = null
            arrow = uci
            solved = solved + n.mistake.key
            prefs.edit().putStringSet(SOLVED, solved).apply()
        } else {
            attempt = Attempt.Wrong
            misses++
            tried = GameLine.san(n.mistake.fen, uci)
        }
    }

    fun showAnswer() {
        attempt = Attempt.Shown
        arrow = note?.mistake?.answers?.firstOrNull()
    }

    fun next() {
        val size = notes?.size ?: return
        if (size == 0) return
        index = (index + 1) % size
        attempt = Attempt.Solving
        tried = null
        arrow = null
    }

    private companion object {
        const val SOLVED = "solved"
    }
}
