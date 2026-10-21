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
    fun enPassantIsALegalMove() {
        // After 2...d5 White can take en passant with exd6.
        val l = line("1. e4 a6 2. e5 d5 *")
        val fen = l.positions.last().fen
        assertTrue(fen, "e5d6" in GameLine.legalMoves(fen))
        val after = l.play(4, "e5d6")!!
        assertEquals("exd6", after.plies.last().san)
        assertNull(pieces(after.positions.last().fen, "d5"))
    }

    private fun pieces(fen: String, sq: String): Char? {
        val rank = fen.substringBefore(' ').split('/')[8 - (sq[1] - '0')]
        var file = 0
        for (c in rank) {
            if (c.isDigit()) file += c - '0' else if (file++ == sq[0] - 'a') return c
        }
        return null
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
    fun sanFromUci() {
        assertEquals("Nf3", GameLine.san(GameLine.START_FEN, "g1f3"))
        assertEquals("a8=Q+", GameLine.san("8/P7/8/8/8/8/8/k6K w - - 0 1", "a7a8q"))
        assertEquals("zz", GameLine.san(GameLine.START_FEN, "zz"))
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

    @Test
    fun setUpPosition() {
        // The start position's pieces give back the start FEN.
        val startPieces = mapOf(
            0 to 'R', 1 to 'N', 2 to 'B', 3 to 'Q', 4 to 'K', 5 to 'B', 6 to 'N', 7 to 'R',
            56 to 'r', 57 to 'n', 58 to 'b', 59 to 'q', 60 to 'k', 61 to 'b', 62 to 'n', 63 to 'r',
        ) + (8..15).associateWith { 'P' } + (48..55).associateWith { 'p' }
        assertEquals(GameLine.START_FEN, Setup.fen(startPieces, whiteToMove = true))
        assertEquals("8/8/8/8/8/8/8/4K2k b - - 0 1", Setup.fen(mapOf(4 to 'K', 7 to 'k'), whiteToMove = false))

        assertNull(Setup.error("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))
        assertEquals(SetupError.KINGS, Setup.error("8/8/8/8/8/8/8/4K3 w - - 0 1"))
        assertEquals(SetupError.PAWNS, Setup.error("P3k3/8/8/8/8/8/8/4K3 w - - 0 1"))
        // Stockfish exits on these, so they cannot be analyzed.
        assertEquals(SetupError.TOO_MANY_PAWNS, Setup.error("rnbqkbnr/ppppppPp/8/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 1"))
        assertEquals(SetupError.TOO_MANY_PIECES, Setup.error("rnbqkbnr/pppppppp/8/8/8/N7/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
        assertNull(Setup.error("4k3/8/8/8/8/8/8/QQQQK3 w - - 0 1"))
        // Black's king is attacked by the rook but it is White's turn.
        assertEquals(SetupError.CHECK, Setup.error("4k3/8/8/8/8/8/8/4RK2 w - - 0 1"))
        assertNull(Setup.error("4k3/8/8/8/8/8/8/4RK2 b - - 0 1"))

        // The PGN replays from the set-up position with no moves.
        val fen = "4k3/8/8/8/8/8/4P3/4K3 b - - 0 1"
        val l = GameLine.from(Pgn.parse(Setup.pgn(fen)))
        assertEquals(fen, l.positions.single().fen)
    }

    @Test
    fun mateAndResignation() {
        val mated = line("1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0")
        assertTrue(mated.positions.last().mate)
        assertEquals("e8", mated.positions.last().check)
        // Check alone is not mate.
        val check = line("1. e4 f5 2. Qh5+ *").positions.last()
        assertEquals("e8", check.check)
        assertTrue(!check.mate)

        fun game(termination: String?, result: String) =
            Pgn.parse((termination?.let { "[Termination \"$it\"]\n" } ?: "") + "[Result \"$result\"]\n\n1. e4 e5 $result")
        assertEquals(Finish.RESIGNED, game("thibault won by resignation", "0-1").finish())
        assertEquals(Finish.RESIGNED, game("Normal", "1-0").finish()) // Lichess: mate is ruled out by the board
        assertEquals(Finish.TIME, game("Grum000 won on time", "0-1").finish())
        assertEquals(Finish.TIME, game("Time forfeit", "0-1").finish())
        assertNull(game("Game drawn by timeout vs insufficient material", "1/2-1/2").finish())
        assertNull(game("Normal", "1/2-1/2").finish())
        assertNull(game(null, "1-0").finish())
    }

    @Test
    fun playsOwnMoves() {
        val game = line("1. e4 e5 2. Nf3 *")
        assertEquals(20, GameLine.legalMoves(GameLine.START_FEN).size)
        // From after 1. e4, play 1... c5 instead: the rest of the game is dropped.
        val sicilian = game.play(1, "c7c5")!!
        assertEquals(listOf("e4", "c5"), sicilian.plies.map { it.san })
        assertEquals(3, sicilian.positions.size)
        assertEquals(game.positions[1], sicilian.positions[1])
        assertTrue(sicilian.positions[2].fen.startsWith("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w"))
        assertNull(game.play(1, "e2e4")) // not Black's move
        // Check and promotion come out right.
        val promo = line("[FEN \"8/P6k/8/8/8/8/8/K7 w - - 0 1\"]\n*")
        assertEquals(listOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), GameLine.legalMoves(promo.positions[0].fen).filter { it.startsWith("a7") }.sortedBy { "qrbn".indexOf(it.last()) })
        val queened = promo.play(0, "a7a8q")!!
        assertEquals("a8=Q", queened.plies[0].san)
        assertNull(queened.positions[1].check)
        val check = line("1. e4 e5 *").play(2, "d1h5")!!.play(3, "b8c6")!!.play(4, "h5f7")!!
        assertEquals("Qxf7", check.plies.last().san)
        assertEquals("e8", check.positions.last().check)
    }
}
