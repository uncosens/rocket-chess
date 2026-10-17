package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.engine.Uci
import com.uncosens.rocketchess.engine.UciInfo
import com.uncosens.rocketchess.engine.UciScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {
    @Test
    fun parsesUciLines() {
        assertEquals(
            UciInfo(14, 2, UciScore.Cp(-23), listOf("e7e5", "g1f3")),
            Uci.parseInfo("info depth 14 seldepth 20 multipv 2 score cp -23 nodes 9 nps 1 time 3 pv e7e5 g1f3"),
        )
        assertEquals(UciScore.Mate(-3), Uci.parseInfo("info depth 9 multipv 1 score mate -3 pv a1a2")!!.score)
        assertEquals(UciScore.Mate(0), Uci.parseInfo("info depth 0 score mate 0")!!.score)
        assertNull(Uci.parseInfo("info depth 10 currmove e2e4 currmovenumber 1"))
        assertNull(Uci.parseInfo("info depth 10 multipv 1 score cp 20 lowerbound pv e2e4"))
        assertNull(Uci.parseInfo("info string NNUE evaluation using nn-1a298aa575a0.nnue"))
        assertEquals("e7e8q", Uci.parseBestMove("bestmove e7e8q ponder a1a2"))
        assertNull(Uci.parseBestMove("bestmove (none)"))
    }

    @Test
    fun scoresBecomeWhiteView() {
        assertEquals(Eval.Cp(35), Eval.fromUci(UciScore.Cp(35), whiteToMove = true))
        assertEquals(Eval.Cp(-35), Eval.fromUci(UciScore.Cp(35), whiteToMove = false))
        assertEquals(Eval.Mate(whiteWins = false, moves = 2), Eval.fromUci(UciScore.Mate(2), whiteToMove = false))
        assertEquals(Eval.Mate(whiteWins = true, moves = 2), Eval.fromUci(UciScore.Mate(-2), whiteToMove = false))
        // Mated now: the side to move lost.
        assertEquals(Eval.Mate(whiteWins = false, moves = 0), Eval.fromUci(UciScore.Mate(0), whiteToMove = true))
        assertEquals(Eval.Mate(whiteWins = true, moves = 0), Eval.fromUci(UciScore.Mate(0), whiteToMove = false))
    }

    @Test
    fun winPercent() {
        assertEquals(50.0, whiteWinPercent(Eval.Cp(0)), 1e-9)
        assertEquals(100 - whiteWinPercent(Eval.Cp(150)), whiteWinPercent(Eval.Cp(-150)), 1e-9)
        assertEquals(63.4, whiteWinPercent(Eval.Cp(150)), 0.1)
        assertEquals(100.0, whiteWinPercent(Eval.Mate(true, 5)), 0.0)
        assertEquals(0.0, whiteWinPercent(Eval.Mate(false, 0)), 0.0)
    }

    @Test
    fun lossIsFromTheMoversView() {
        // White drops from +1.50 to 0.00: white lost win%, black gained it.
        assertEquals(13.4, winLoss(Eval.Cp(150), Eval.Cp(0), whiteMoved = true), 0.1)
        assertEquals(-13.4, winLoss(Eval.Cp(150), Eval.Cp(0), whiteMoved = false), 0.1)
    }

    @Test
    fun classBoundaries() {
        fun c(loss: Double) = classify("a2a3", "e2e4", loss)
        assertEquals(MoveClass.Best, classify("e2e4", "e2e4", 40.0))
        assertEquals(MoveClass.Excellent, c(-3.0))
        assertEquals(MoveClass.Excellent, c(0.0))
        assertEquals(MoveClass.Excellent, c(1.999))
        assertEquals(MoveClass.Good, c(2.0))
        assertEquals(MoveClass.Good, c(4.999))
        assertEquals(MoveClass.Inaccuracy, c(5.0))
        assertEquals(MoveClass.Inaccuracy, c(9.999))
        assertEquals(MoveClass.Mistake, c(10.0))
        assertEquals(MoveClass.Mistake, c(14.999))
        assertEquals(MoveClass.Blunder, c(15.0))
    }

    @Test
    fun mateRules() {
        // Longer mate is still a forced win: no loss.
        assertEquals(0.0, winLoss(Eval.Mate(true, 2), Eval.Mate(true, 6), whiteMoved = true), 0.0)
        // Winning mate thrown away into an equal position: blunder.
        assertEquals(MoveClass.Blunder, classify("a", "b", winLoss(Eval.Mate(true, 3), Eval.Cp(0), whiteMoved = true)))
        // Walking into a mate from a small edge: blunder.
        assertEquals(MoveClass.Blunder, classify("a", "b", winLoss(Eval.Cp(50), Eval.Mate(false, 4), whiteMoved = true)))
        // Black finds mate: no loss for Black.
        assertTrue(winLoss(Eval.Cp(-300), Eval.Mate(false, 0), whiteMoved = false) <= 0)
    }

    @Test
    fun gradeUsesPositionsBeforeAndAfter() {
        val line = GameLine.from(Pgn.parse("1. e4 e5 2. Qh5 *"))
        val evals = listOf(
            PositionEval(Eval.Cp(30), "e2e4", null, 14),
            PositionEval(Eval.Cp(30), "e7e5", null, 14),
            PositionEval(Eval.Cp(35), "g1f3", null, 14),
            null,
        )
        assertEquals(MoveClass.Best, Analyzer.grade(line, evals, 0)!!.cls)
        assertEquals(MoveClass.Best, Analyzer.grade(line, evals, 1)!!.cls)
        assertNull(Analyzer.grade(line, evals, 2)) // position after Qh5 not analyzed yet
    }
}
