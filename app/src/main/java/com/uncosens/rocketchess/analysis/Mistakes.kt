package com.uncosens.rocketchess.analysis

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import com.uncosens.rocketchess.chess.GameLine

/**
 * A Mistake or Blunder the user made, as a puzzle: find a better move in [fen].
 * [answers] are the engine's first choice, plus its second when that loses less than Good's line.
 * [lastMove] is the opponent's move that led to [fen], as (from, to).
 * [themes] are the Lichess puzzle themes of the tactics missed or allowed that are seen.
 */
data class Mistake(
    val fen: String,
    val played: String,
    val answers: List<String>,
    val cls: MoveClass,
    val lastMove: Pair<String, String>?,
    val themes: List<String> = emptyList(),
) {
    /** Same position and same wrong move: one puzzle, even if the game is saved twice. */
    val key: String get() = "$fen|$played"
}

object Mistakes {
    /** The user's Mistakes and Blunders in one analyzed game, in game order. */
    fun of(line: GameLine, evals: List<PositionEval?>, userWhite: Boolean): List<Mistake> =
        line.plies.indices.mapNotNull { i ->
            if ((line.positions[i].fen.split(' ')[1] == "w") != userWhite) return@mapNotNull null
            val cls = Analyzer.grade(line, evals, i)?.cls ?: return@mapNotNull null
            if (cls != MoveClass.Mistake && cls != MoveClass.Blunder) return@mapNotNull null
            val before = evals[i] ?: return@mapNotNull null
            val after = evals[i + 1] ?: return@mapNotNull null
            val best = before.best ?: return@mapNotNull null
            val second = before.secondBest?.takeIf { before.second != null && winLoss(before.eval, before.second, userWhite) < AnalysisPolicy.GOOD_BELOW }
            val last = line.plies.getOrNull(i - 1)
            val themes = themes(line.positions[i].fen, best, before.eval, line.positions[i + 1].fen, after, userWhite)
            Mistake(line.positions[i].fen, line.plies[i].uci, listOfNotNull(best, second).filter { it != line.plies[i].uci }, cls, last?.let { it.from to it.to }, themes)
        }

    /**
     * The tactics behind a mistake: a mate the user missed or allowed, a fork, a free piece. Looks at the
     * user's best move in [fen] and the opponent's best reply in [fenAfter].
     */
    fun themes(fen: String, best: String, eval: Eval, fenAfter: String, after: PositionEval, userWhite: Boolean): List<String> {
        val out = mutableSetOf<String>()
        if (eval is Eval.Mate && eval.whiteWins == userWhite && eval.moves > 0) out += mateTheme(eval.moves)
        val e = after.eval
        if (e is Eval.Mate && e.whiteWins != userWhite && e.moves > 0) out += mateTheme(e.moves)
        val reply = after.best
        if (fork(fen, best) || (reply != null && fork(fenAfter, reply))) out += "fork"
        if (hanging(fen, best) || (reply != null && hanging(fenAfter, reply))) out += "hangingPiece"
        return out.toList()
    }

    private fun mateTheme(moves: Int) = if (moves <= 5) "mateIn$moves" else "mate"

    private val VALUE = mapOf(PieceType.KNIGHT to 3, PieceType.BISHOP to 3, PieceType.ROOK to 5, PieceType.QUEEN to 9, PieceType.KING to 100)

    /** After [uci], the moved piece attacks two or more enemy pieces worth at least a knight. */
    private fun fork(fen: String, uci: String): Boolean = runCatching {
        val b = Board().apply { loadFromFen(fen) }
        val me = b.sideToMove
        val move = b.legalMoves().first { it.toString() == uci }
        b.doMove(move)
        Square.values().count { sq ->
            val p = b.getPiece(sq)
            sq != Square.NONE && p != Piece.NONE && p.pieceSide != me && p.pieceType in VALUE &&
                (b.squareAttackedBy(sq, me) and move.to.bitboard) != 0L
        } >= 2
    }.getOrDefault(false)

    /** [uci] takes a piece (not a pawn) that nothing defends. */
    private fun hanging(fen: String, uci: String): Boolean = runCatching {
        val b = Board().apply { loadFromFen(fen) }
        val move = b.legalMoves().first { it.toString() == uci }
        val target = b.getPiece(move.to)
        target != Piece.NONE && target.pieceType in VALUE && b.squareAttackedBy(move.to, b.sideToMove.flip()) == 0L
    }.getOrDefault(false)
}
