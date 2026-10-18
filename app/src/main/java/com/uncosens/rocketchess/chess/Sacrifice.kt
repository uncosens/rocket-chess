package com.uncosens.rocketchess.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * A move is a piece sacrifice when the opponent can win the moved piece back for a net gain of at
 * least [MIN_LOSS]: a static exchange on its landing square, minus what the move itself took.
 */
object Sacrifice {
    /** A minor piece for a pawn, or the exchange. Losing only a pawn does not count. */
    private const val MIN_LOSS = 200

    /** True if [move], legal on [board], is a sacrifice. [board] is left as it was. */
    fun of(board: Board, move: Move): Boolean {
        val taken = takenBy(board, move)
        board.doMove(move)
        val lost = exchange(board, move.to)
        board.undoMove()
        return lost - taken >= MIN_LOSS
    }

    private fun value(type: PieceType?) = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT, PieceType.BISHOP -> 300
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        else -> 0
    }

    // The king captures last: it may only take a piece nobody defends.
    private fun attackerOrder(type: PieceType?) = if (type == PieceType.KING) Int.MAX_VALUE else value(type)

    /** Material [m] gains on [board]: what it captures (en passant takes a pawn), plus any promotion. */
    private fun takenBy(board: Board, m: Move): Int {
        val target = board.getPiece(m.to)
        val pawn = board.getPiece(m.from).pieceType == PieceType.PAWN
        val captured = when {
            target != Piece.NONE -> value(target.pieceType)
            pawn && m.from.file != m.to.file -> value(PieceType.PAWN)
            else -> 0
        }
        val promoted = if (m.promotion != Piece.NONE) value(m.promotion.pieceType) - value(PieceType.PAWN) else 0
        return captured + promoted
    }

    /**
     * Net material the side to move can win by capturing on [square], cheapest piece first, and
     * stopping as soon as going on would lose. Only legal moves, so pins and checks count.
     */
    private fun exchange(board: Board, square: Square): Int {
        val capture = board.legalMoves().filter { it.to == square }
            .minByOrNull { attackerOrder(board.getPiece(it.from).pieceType) } ?: return 0
        val gain = value(board.getPiece(square).pieceType)
        board.doMove(capture)
        val net = gain - exchange(board, square)
        board.undoMove()
        return maxOf(0, net)
    }
}
