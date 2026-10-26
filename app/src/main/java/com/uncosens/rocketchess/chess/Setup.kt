package com.uncosens.rocketchess.chess

import com.github.bhlangonijr.chesslib.Board

enum class SetupError { KINGS, PAWNS, TOO_MANY_PAWNS, TOO_MANY_PIECES, CHECK }

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
        // Stockfish exits on a side with more than 8 pawns or 16 pieces.
        if (board.count { it == 'P' } > 8 || board.count { it == 'p' } > 8) return SetupError.TOO_MANY_PAWNS
        if (board.count { it.isUpperCase() } > 16 || board.count { it.isLowerCase() } > 16) return SetupError.TOO_MANY_PIECES
        // With the turn handed over, a king under attack means the side not to move is in check.
        val fields = fen.split(' ').toMutableList()
        fields[1] = if (fields[1] == "w") "b" else "w"
        val other = Board().apply { loadFromFen(fields.joinToString(" ")) }
        return if (other.isKingAttacked) SetupError.CHECK else null
    }

    /** A PGN that starts from [fen], with [moves] (UCI) played from there. */
    fun pgn(fen: String, moves: List<String> = emptyList()): String {
        val f = fen.split(' ')
        var number = f.getOrNull(5)?.toIntOrNull() ?: 1
        var white = f[1] == "w"
        var at = fen
        val text = buildString {
            for ((i, uci) in moves.withIndex()) {
                if (white) append("$number. ") else if (i == 0) append("$number... ")
                append(GameLine.san(at, uci)).append(' ')
                at = GameLine.after(at, uci)?.fen ?: break
                if (!white) number++
                white = !white
            }
        }
        return "[Event \"Set up position\"]\n[White \"White\"]\n[Black \"Black\"]\n[SetUp \"1\"]\n[FEN \"$fen\"]\n[Result \"*\"]\n\n$text*\n"
    }
}
