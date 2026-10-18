package com.uncosens.rocketchess.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.move.Move

/**
 * En Croissant's sacrifice test (src-tauri/src/chess.rs): a move is a sacrifice when a captures-only
 * material search says it gives up more than 100 (a pawn is 90) against the mover's best option.
 */
object Sacrifice {
    /** True if [move], legal on [board], is a sacrifice. [board] is left as it was. */
    fun of(board: Board, move: Move): Boolean {
        val before = naiveEval(board)
        board.doMove(move)
        val over = board.legalMoves().isEmpty() || board.isInsufficientMaterial
        val after = if (over) 0 else -naiveEval(board)
        board.undoMove()
        return !over && before > after + 100
    }

    private fun value(type: PieceType?) = when (type) {
        PieceType.PAWN -> 90
        PieceType.KNIGHT, PieceType.BISHOP -> 300
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 1000
        else -> 0
    }

    /** Material for the side to move; being mated is -10000. */
    private fun material(board: Board): Int {
        if (board.isMated) return -10000
        var own = 0
        var other = 0
        for (p in board.boardToArray()) {
            if (p == Piece.NONE) continue
            if (p.pieceSide == board.sideToMove) own += value(p.pieceType) else other += value(p.pieceType)
        }
        return own - other
    }

    /** Value of the piece [m] takes, or null if it takes nothing. En passant takes a pawn. */
    private fun captured(board: Board, m: Move): Int? {
        val target = board.getPiece(m.to)
        if (target != Piece.NONE) return value(target.pieceType)
        val pawn = board.getPiece(m.from).pieceType == PieceType.PAWN
        return if (pawn && m.from.file != m.to.file) value(PieceType.PAWN) else null
    }

    /** Fail-hard alpha-beta over captures only, biggest capture first. */
    private fun qsearch(board: Board, alphaIn: Int, beta: Int): Int {
        var alpha = alphaIn
        val standPat = material(board)
        if (standPat >= beta) return beta
        if (alpha < standPat) alpha = standPat
        val captures = board.legalMoves().mapNotNull { m -> captured(board, m)?.let { m to it } }.sortedByDescending { it.second }
        for ((m, _) in captures) {
            board.doMove(m)
            val score = -qsearch(board, -beta, -alpha)
            board.undoMove()
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** Best material the side to move can reach: one move, then captures. */
    private fun naiveEval(board: Board): Int = board.legalMoves().maxOfOrNull { m ->
        board.doMove(m)
        val v = -qsearch(board, Int.MIN_VALUE, Int.MAX_VALUE)
        board.undoMove()
        v
    } ?: Int.MIN_VALUE
}
