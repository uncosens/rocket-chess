package com.uncosens.rocketchess.chess

import com.github.bhlangonijr.chesslib.Board

enum class SetupError { KINGS, PAWNS, CHECK }

/** A position set up by hand. Pieces map a square index (a1 = 0) to a FEN letter. */
object Setup {
    /** FEN for [pieces]. Castling is allowed where king and rook stand on their start squares; no en passant. */
    fun fen(pieces: Map<Int, Char>, whiteToMove: Boolean): String {
        val rows = (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val p = pieces[rank * 8 + file]
                    if (p == null) {
                        empty++
                    } else {
                        if (empty > 0) append(empty)
                        empty = 0
                        append(p)
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val castle = buildString {
            if (pieces[4] == 'K' && pieces[7] == 'R') append('K')
            if (pieces[4] == 'K' && pieces[0] == 'R') append('Q')
            if (pieces[60] == 'k' && pieces[63] == 'r') append('k')
            if (pieces[60] == 'k' && pieces[56] == 'r') append('q')
        }.ifEmpty { "-" }
        return "$rows ${if (whiteToMove) "w" else "b"} $castle - 0 1"
    }

    /** What makes [fen] unplayable, or null when it can be analyzed. */
    fun error(fen: String): SetupError? {
        val board = fen.substringBefore(' ')
        if (board.count { it == 'K' } != 1 || board.count { it == 'k' } != 1) return SetupError.KINGS
        val ranks = board.split('/')
        if (ranks.first().any { it in "Pp" } || ranks.last().any { it in "Pp" }) return SetupError.PAWNS
        // With the turn handed over, a king under attack means the side not to move is in check.
        val fields = fen.split(' ').toMutableList()
        fields[1] = if (fields[1] == "w") "b" else "w"
        val other = Board().apply { loadFromFen(fields.joinToString(" ")) }
        return if (other.isKingAttacked) SetupError.CHECK else null
    }

    /** A PGN with no moves that starts from [fen]. */
    fun pgn(fen: String) =
        "[Event \"Set up position\"]\n[White \"White\"]\n[Black \"Black\"]\n[SetUp \"1\"]\n[FEN \"$fen\"]\n[Result \"*\"]\n\n*\n"
}
