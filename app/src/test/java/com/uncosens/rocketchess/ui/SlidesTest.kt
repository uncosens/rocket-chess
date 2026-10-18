package com.uncosens.rocketchess.ui

import com.uncosens.rocketchess.analysis.Eval
import com.uncosens.rocketchess.chess.GameLine
import org.junit.Assert.assertEquals
import org.junit.Test

class SlidesTest {
    // Square index, a1 = 0.
    private fun sq(name: String) = (name[0] - 'a') + (name[1] - '1') * 8

    @Test
    fun oneMoveSlidesOnePiece() {
        val after = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
        assertEquals(mapOf(sq("e4") to sq("e2")), slides(pieces(GameLine.START_FEN), pieces(after)))
    }

    @Test
    fun goingBackSlidesThePieceBack() {
        val after = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
        assertEquals(mapOf(sq("e2") to sq("e4")), slides(pieces(after), pieces(GameLine.START_FEN)))
    }

    @Test
    fun castlingSlidesKingAndRook() {
        val before = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
        val after = "r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1"
        assertEquals(mapOf(sq("g1") to sq("e1"), sq("f1") to sq("h1")), slides(pieces(before), pieces(after)))
    }

    @Test
    fun captureSlidesOnlyTheCapturer() {
        val before = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"
        val after = "4k3/8/8/3P4/8/8/8/4K3 b - - 0 1"
        assertEquals(mapOf(sq("d5") to sq("e4")), slides(pieces(before), pieces(after)))
    }

    @Test
    fun materialEdgeAndScoreView() {
        assertEquals(MaterialEdge("", 0) to MaterialEdge("", 0), materialEdge(GameLine.START_FEN))
        // White is a knight up; Black is a pawn up: White leads by 2.
        val fen = "r1bqkbnr/pppppppp/8/8/8/8/1PPPPPPP/RNBQKBNR w KQkq - 0 1"
        assertEquals(MaterialEdge("n", 2) to MaterialEdge("p", 0), materialEdge(fen))
        // A promoted queen counts as a queen.
        assertEquals(MaterialEdge("qq", 18) to MaterialEdge("", 0), materialEdge("4k3/8/8/8/8/8/8/Q3K2Q w - - 0 1"))

        assertEquals("+0.35", Eval.Cp(35).text())
        assertEquals("-0.35", Eval.Cp(35).text(whiteView = false))
        assertEquals("#-2", Eval.Mate(whiteWins = true, moves = 2).text(whiteView = false))
        assertEquals("#2", Eval.Mate(whiteWins = false, moves = 2).text(whiteView = false))
    }
}
