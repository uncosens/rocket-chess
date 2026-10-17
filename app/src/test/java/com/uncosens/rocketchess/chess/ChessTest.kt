package com.uncosens.rocketchess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChessTest {
    private fun line(pgn: String) = GameLine.from(Pgn.parse(pgn))

    private fun error(pgn: String): PgnException = try {
        line(pgn)
        fail("expected PgnException")
        throw AssertionError()
    } catch (e: PgnException) {
        e
    }

    @Test
    fun operaGameFixture() {
        val text = javaClass.classLoader!!.getResource("opera.pgn")!!.readText()
        val game = Pgn.parse(text)
        assertEquals("Paul Morphy", game.tags["White"])
        assertEquals("1-0", game.result)
        val line = GameLine.from(game)
        assertEquals(33, line.plies.size)
        assertEquals(34, line.positions.size)
        assertEquals("e1c1", line.plies[22].uci) // 12. O-O-O
        assertEquals("Rd8", line.plies.last().san)
        assertEquals("e8", line.positions.last().check)
        assertEquals("1n1Rkb1r/p4ppp/4q3/4p1B1/4P3/8/PPP2PPP/2K5 b k - 1 17", line.positions.last().fen)
    }

    @Test
    fun commentsVariationsNagsAreSkipped() {
        val l = line("1. e4 {best by test} e5 (1... c5 2. Nf3 (2. c3)) 2. Nf3 \$1 Nc6 ; note\n3. Bb5!? a6 *")
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), l.plies.map { it.san })
    }

    @Test
    fun castlingEnPassantPromotion() {
        assertEquals("e1g1", line("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O *").plies.last().uci)
        assertEquals("e1g1", line("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. 0-0 *").plies.last().uci)
        val ep = line("1. e4 Nf6 2. e5 d5 3. exd6 *")
        assertTrue(ep.positions.last().fen.startsWith("rnbqkb1r/ppp1pppp/3P1n2/8/"))
        val promo = line("[FEN \"8/P7/8/8/8/8/8/k6K w - - 0 1\"]\n1. a8=Q+ *")
        assertEquals("a7a8q", promo.plies[0].uci)
        assertEquals("a1", promo.positions.last().check)
    }

    @Test
    fun blackMovesFirstFromFen() {
        val l = line("[SetUp \"1\"]\n[FEN \"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1\"]\n1... e5 2. Nf3 *")
        assertEquals(listOf("e7e5", "g1f3"), l.plies.map { it.uci })
    }

    @Test
    fun incompleteGameAndMoveNumbersWithoutSpaces() {
        val game = Pgn.parse("1.e4 e5 2.Nf3")
        assertNull(game.result)
        assertEquals(3, GameLine.from(game).plies.size)
    }

    @Test
    fun disambiguation() {
        val fen = "[FEN \"4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1\"]\n"
        assertEquals("b1d2", line(fen + "1. Nbd2 *").plies[0].uci)
        assertEquals("ambiguous move \"Nd2\"", error(fen + "1. Nd2 *").reason)
    }

    @Test
    fun errorsHavePositions() {
        error("1. e4 e5\n2. Ke3 *").run {
            assertEquals("illegal move \"Ke3\"", reason)
            assertEquals(2, line); assertEquals(4, column)
        }
        error("1. e4 {open comment").run { assertEquals("comment is not closed", reason); assertEquals(7, column) }
        assertEquals("unexpected ')'", error("1. e4 ) e5").reason)
        assertEquals("variation is not closed", error("1. e4 (1. d4 e5").reason)
        assertEquals("bad FEN tag", error("[FEN \"not a fen\"]\n1. e4").reason)
        assertEquals("no game found", error("   ").reason)
        assertEquals("bad move \"e9\"", error("1. e9").reason)
    }
}
