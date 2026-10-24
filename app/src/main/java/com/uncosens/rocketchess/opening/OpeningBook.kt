package com.uncosens.rocketchess.opening

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.CastleRight
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.uncosens.rocketchess.chess.GameLine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Games won by White, drawn, and won by Black. */
data class Tally(val white: Int, val draws: Int, val black: Int) {
    val games get() = white + draws + black
    operator fun plus(o: Tally) = Tally(white + o.white, draws + o.draws, black + o.black)

    companion object {
        val ZERO = Tally(0, 0, 0)
    }
}

/** One tally per rating band; [band] null adds them all up. */
private fun List<Tally>.of(band: Int?) = if (band == null) fold(Tally.ZERO, Tally::plus) else this[band]

/** A move played in a book position. [uci] is like "e2e4". */
data class BookMove(val uci: String, val bands: List<Tally>) {
    fun tally(band: Int?) = bands.of(band)
}

/** Games that reached a position, and the moves played there, most played first. */
data class BookPosition(val bands: List<Tally>, val moves: List<BookMove>) {
    fun tally(band: Int?) = bands.of(band)

    /** Moves played in [band], most played first. */
    fun moves(band: Int?) = moves.filter { it.tally(band).games > 0 }.sortedByDescending { it.tally(band).games }
}

/**
 * Lichess rapid game statistics by position, built by scripts/opening_book.py.
 * Positions are found by [positionKey] with a binary search over the sorted index.
 */
class OpeningBook(bytes: ByteArray) {
    private val buf = ByteBuffer.wrap(bytes)
    /** Lower edge of each band after the first, as mean Lichess rapid ratings of the two players. */
    val lichessEdges: List<Int>
    /** The same edges as Chess.com rapid ratings (1200, 1500, ...), for labels. */
    val chesscomEdges: List<Int>
    private val bands: Int
    private val count: Int
    private val indexStart: Int
    private val dataStart: Int

    init {
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RCOB" && bytes[4].toInt() == 2) { "not an opening book" }
        bands = bytes[5].toInt()
        lichessEdges = List(bands - 1) { buf.getShort(6 + 2 * it).toInt() and 0xFFFF }
        chesscomEdges = List(bands - 1) { buf.getShort(6 + 2 * (bands - 1 + it)).toInt() and 0xFFFF }
        val head = 6 + 4 * (bands - 1)
        count = buf.getInt(head)
        indexStart = head + 4
        dataStart = indexStart + count * INDEX
    }

    fun lookup(fen: String): BookPosition? = lookup(positionKey(fen))

    fun lookup(key: Long): BookPosition? {
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val at = indexStart + mid * INDEX
            val c = java.lang.Long.compareUnsigned(buf.getLong(at), key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return read(dataStart + buf.getInt(at + 8))
            }
        }
        return null
    }

    /** How many plies of [line], from its start, are book moves: each played in [BOOK_SHARE] or more of its position's games. */
    fun bookPlies(line: GameLine): Int {
        for (i in line.plies.indices) {
            val p = lookup(line.positions[i].fen) ?: return i
            val m = p.moves.firstOrNull { it.uci == line.plies[i].uci } ?: return i
            if (m.tally(null).games < p.tally(null).games * BOOK_SHARE) return i
        }
        return line.plies.size
    }

    private fun read(start: Int): BookPosition {
        var pos = start
        fun varint(): Int {
            var n = 0
            var shift = 0
            while (true) {
                val b = buf.get(pos++).toInt() and 0xFF
                n = n or ((b and 0x7F) shl shift)
                if (b < 0x80) return n
                shift += 7
            }
        }
        fun tallies() = List(bands) { Tally(varint(), varint(), varint()) }
        val totals = tallies()
        val moves = List(varint()) {
            val code = buf.getShort(pos).toInt() and 0xFFFF
            pos += 2
            BookMove(uci(code), tallies())
        }
        return BookPosition(totals, moves)
    }

    companion object {
        private const val INDEX = 12
        /** The file keeps rare moves too; a book move needs at least this share of the games. */
        const val BOOK_SHARE = 0.01

        /** "e7e8q" from from | to << 6 | promotion << 12 (0 none, 1 N, 2 B, 3 R, 4 Q). */
        internal fun uci(code: Int): String {
            fun sq(i: Int) = "${'a' + i % 8}${i / 8 + 1}"
            val promo = when (code shr 12) {
                1 -> "n"
                2 -> "b"
                3 -> "r"
                4 -> "q"
                else -> ""
            }
            return sq(code and 63) + sq(code shr 6 and 63) + promo
        }

        private val ORDER = listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING)

        /**
         * 64-bit key of a position, the same as scripts/opening_book.py: SHA-1 of the 12 piece bitboards,
         * side to move, castling (only with king and rook at home) and the en passant square (only when legal).
         */
        fun positionKey(fen: String): Long {
            val board = Board().apply { loadFromFen(fen) }
            val code = ByteBuffer.allocate(99).order(ByteOrder.LITTLE_ENDIAN)
            for (side in listOf(Side.WHITE, Side.BLACK)) for (type in ORDER) code.putLong(board.getBitboard(Piece.make(side, type)))
            code.put(if (board.sideToMove == Side.WHITE) 1 else 0)
            code.put(castling(board).toByte())
            val ep = board.enPassant
            val legalEp = ep != Square.NONE && board.legalMoves().any { it.to == ep && board.getPiece(it.from).pieceType == PieceType.PAWN }
            code.put((if (legalEp) ep.ordinal else 64).toByte())
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-1").digest(code.array())).long
        }

        /** 1 K, 2 Q, 4 k, 8 q; a right counts only while its king and rook are on their start squares. */
        private fun castling(b: Board): Int {
            fun has(side: Side, kingSide: Boolean): Boolean {
                val r = b.getCastleRight(side)
                val right = r == CastleRight.KING_AND_QUEEN_SIDE || r == (if (kingSide) CastleRight.KING_SIDE else CastleRight.QUEEN_SIDE)
                val white = side == Side.WHITE
                val king = if (white) Square.E1 else Square.E8
                val rook = if (white) (if (kingSide) Square.H1 else Square.A1) else (if (kingSide) Square.H8 else Square.A8)
                return right && b.getPiece(king) == Piece.make(side, PieceType.KING) && b.getPiece(rook) == Piece.make(side, PieceType.ROOK)
            }
            return (if (has(Side.WHITE, true)) 1 else 0) or (if (has(Side.WHITE, false)) 2 else 0) or
                (if (has(Side.BLACK, true)) 4 else 0) or (if (has(Side.BLACK, false)) 8 else 0)
        }
    }
}
