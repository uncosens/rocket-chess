package com.uncosens.rocketchess.opening

import com.uncosens.rocketchess.analysis.MoveClass
import com.uncosens.rocketchess.analysis.bookGrade
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!

    // opening-book-test.bin: scripts/opening_book.py write with MIN_GAMES 300 on 159k rapid games of 2026-09.
    private val book = OpeningBook(resource("opening-book-test.bin").readBytes())

    private fun line(moves: String) = GameLine.from(Pgn.parse("$moves *"))

    @Test
    fun keysMatchTheBuildScript() {
        // Made by scripts/opening_book.py vectors; covers en passant, castling and pinned en passant cases.
        for (row in resource("opening-keys.tsv").readText().lines().filter { it.isNotBlank() }) {
            val (fen, key) = row.split('\t')
            assertEquals(fen, java.lang.Long.parseUnsignedLong(key, 16), OpeningBook.positionKey(fen))
        }
    }

    @Test
    fun transpositionsShareAKey() {
        val a = line("1. Nf3 d5 2. d4").positions.last().fen
        val b = line("1. d4 d5 2. Nf3").positions.last().fen
        assertEquals(OpeningBook.positionKey(a), OpeningBook.positionKey(b))
    }

    @Test
    fun readsPositionsAndBands() {
        assertEquals(listOf(1200, 1600, 2000), book.bandEdges)
        val start = book.lookup(GameLine.START_FEN)!!
        assertEquals(158_898, start.tally(null).games)
        assertEquals(Tally(4771, 822, 4477), start.tally(3))
        assertEquals(listOf("e2e4", "d2d4", "g1f3", "c2c4"), start.moves(null).take(4).map { it.uci })
        assertEquals(Tally(2645, 459, 2610), start.moves.first { it.uci == "e2e4" }.tally(3))
        assertEquals(103_568, start.moves.first { it.uci == "e2e4" }.tally(null).games)
        // A position nobody reaches in rapid games.
        assertNull(book.lookup("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))
    }

    @Test
    fun bookPliesStopAtTheFirstMoveOutOfBook() {
        assertEquals(5, book.bookPlies(line("1. e4 e5 2. Nf3 Nc6 3. Bc4")))
        assertEquals(3, book.bookPlies(line("1. e4 e5 2. Qh5 Ke7")))
        assertEquals(0, book.bookPlies(line("1. a3")))
        // 1. f4 is in the file but has 0.86% of the games: below BOOK_SHARE. 1. e3 has 1.52%.
        assertEquals(0, book.bookPlies(line("1. f4")))
        assertEquals(1, book.bookPlies(line("1. e3")))
    }

    @Test
    fun movesDecodeFromCodes() {
        assertEquals("e2e4", OpeningBook.uci(12 or (28 shl 6)))
        assertEquals("e7e8q", OpeningBook.uci(52 or (60 shl 6) or (4 shl 12)))
        assertEquals("a2a1n", OpeningBook.uci(8 or (0 shl 6) or (1 shl 12)))
    }

    @Test
    fun namesComeFromTheDeepestNamedPosition() {
        val italian = line("1. e4 e5 2. Nf3 Nc6 3. Bc4 a6")
        fun key(ply: Int) = java.lang.Long.toHexString(OpeningBook.positionKey(italian.positions[ply].fen))
        val names = OpeningNames.parse("${key(1)}\tB00\tKing's Pawn Game\n${key(5)}\tC50\tItalian Game\n")
        assertEquals(Opening("C50", "Italian Game"), names.of(italian, 6))
        assertEquals(Opening("B00", "King's Pawn Game"), names.of(italian, 3))
        assertNull(names.of(italian, 0))
    }

    @Test
    fun bookReplacesGoodGradesOnly() {
        assertEquals(MoveClass.Book, bookGrade(MoveClass.Best, inBook = true))
        assertEquals(MoveClass.Book, bookGrade(MoveClass.Fine, inBook = true))
        assertEquals(MoveClass.Weak, bookGrade(MoveClass.Weak, inBook = true))
        assertEquals(MoveClass.Blunder, bookGrade(MoveClass.Blunder, inBook = true))
        assertEquals(MoveClass.Best, bookGrade(MoveClass.Best, inBook = false))
    }
}
