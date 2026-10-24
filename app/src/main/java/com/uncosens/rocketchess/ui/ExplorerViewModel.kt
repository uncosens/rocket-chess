package com.uncosens.rocketchess.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.Setup

/** A line of moves from the start position, walked in the opening explorer. */
class ExplorerViewModel : ViewModel() {
    var line by mutableStateOf(GameLine.from(Pgn.parse(Setup.pgn(GameLine.START_FEN))))
        private set
    /** Index into line.positions. Moves after it stay, so Next can go forward again. */
    var ply by mutableStateOf(0)
        private set
    var flipped by mutableStateOf(false)

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
}
