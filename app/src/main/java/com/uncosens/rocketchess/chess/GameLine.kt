package com.uncosens.rocketchess.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

/** One half-move. Squares are names like "e2". [uci] is the engine notation, e.g. "e7e8q". */
data class Ply(val san: String, val uci: String, val from: String, val to: String)

/** A position. [check] is the square of the king in check, if any. */
data class Position(val fen: String, val check: String?)

/** A game as a straight line: positions.size == plies.size + 1. */
class GameLine(val plies: List<Ply>, val positions: List<Position>) {
    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        /** Replays [game] with full legality checks. Throws [PgnException] at the first bad move. */
        fun from(game: PgnGame): GameLine {
            val board = Board()
            val fen = game.tags["FEN"]?.trim()
            val start = if (fen.isNullOrEmpty()) START_FEN else fen
            if (!FEN.matches(start) || runCatching { board.loadFromFen(start) }.isFailure) {
                throw PgnException("bad FEN tag", 1, 1)
            }
            val plies = mutableListOf<Ply>()
            val positions = mutableListOf(position(board))
            for (token in game.moves) {
                val move = resolve(board, token)
                val uci = uciOf(move)
                plies += Ply(token.text.trimEnd('+', '#', '!', '?'), uci, uci.substring(0, 2), uci.substring(2, 4))
                board.doMove(move)
                positions += position(board)
            }
            return GameLine(plies, positions)
        }

        /** SAN for a UCI move in a position, e.g. "g1f3" -> "Nf3". Falls back to the UCI text. */
        fun san(fen: String, uci: String): String = runCatching {
            val board = Board().apply { loadFromFen(fen) }
            val move = board.legalMoves().first { uciOf(it) == uci }
            MoveList(fen).apply { add(move) }.toSanArray()[0]
        }.getOrDefault(uci)

        private fun uciOf(move: Move): String {
            val promo = move.promotion.takeIf { it != Piece.NONE }?.let { letter(it.pieceType).lowercaseChar() }
            return move.from.value().lowercase() + move.to.value().lowercase() + (promo ?: "")
        }

        private fun position(board: Board) = Position(
            board.fen,
            if (board.isKingAttacked) board.getKingSquare(board.sideToMove).value().lowercase() else null,
        )

        private fun resolve(board: Board, token: SanToken): Move {
            val san = token.text.trimEnd('+', '#', '!', '?')
            val legal = board.legalMoves()
            val castle = when (san.replace('0', 'O')) {
                "O-O" -> 'G'
                "O-O-O" -> 'C'
                else -> null
            }
            val matches = if (castle != null) {
                legal.filter {
                    board.getPiece(it.from).pieceType == PieceType.KING &&
                        it.from.value()[0] == 'E' && it.to.value()[0] == castle
                }
            } else {
                val m = SAN.matchEntire(san) ?: throw PgnException("bad move \"${token.text}\"", token.line, token.column)
                val (piece, file, rank, dest, promo) = m.destructured
                legal.filter {
                    val from = it.from.value().lowercase()
                    letter(board.getPiece(it.from).pieceType) == (piece.firstOrNull() ?: 'P') &&
                        it.to.value().lowercase() == dest &&
                        (file.isEmpty() || from[0] == file[0]) &&
                        (rank.isEmpty() || from[1] == rank[0]) &&
                        (it.promotion == Piece.NONE) == promo.isEmpty() &&
                        (promo.isEmpty() || letter(it.promotion.pieceType) == promo[0])
                }
            }
            return when (matches.size) {
                1 -> matches[0]
                0 -> throw PgnException("illegal move \"${token.text}\"", token.line, token.column)
                else -> throw PgnException("ambiguous move \"${token.text}\"", token.line, token.column)
            }
        }

        private fun letter(type: PieceType?) = when (type) {
            PieceType.KNIGHT -> 'N'
            PieceType.BISHOP -> 'B'
            PieceType.ROOK -> 'R'
            PieceType.QUEEN -> 'Q'
            PieceType.KING -> 'K'
            else -> 'P'
        }

        private val SAN = Regex("^([NBRQK])?([a-h])?([1-8])?x?([a-h][1-8])(?:=?([NBRQ]))?$")
        private val FEN = Regex("^([pnbrqkPNBRQK1-8]+/){7}[pnbrqkPNBRQK1-8]+ [wb] (-|[KQkq]+) (-|[a-h][36])( \\d+ \\d+)?$")
    }
}
