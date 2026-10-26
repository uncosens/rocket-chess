package com.uncosens.rocketchess.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakePuzzlesTest {
    private fun pe(eval: Eval, best: String?, second: Eval? = null) = PositionEval(eval, best, second, 18)

    /** A search that answers the calls in order. */
    private fun searches(vararg results: PositionEval): (String) -> PositionEval {
        val left = results.toMutableList()
        return { left.removeAt(0) }
    }

    @Test
    fun onlyMoveNeedsGapAndWorseResult() {
        // Even vs losing: 0 cp vs -200 cp is a 21% gap.
        assertTrue(MistakePuzzles.onlyMove(Eval.Cp(0), Eval.Cp(-200), moverWhite = true))
        // Both win: +800 vs +400 is a big cp gap but the same result.
        assertFalse(MistakePuzzles.onlyMove(Eval.Cp(800), Eval.Cp(400), moverWhite = true))
        // Close moves.
        assertFalse(MistakePuzzles.onlyMove(Eval.Cp(50), Eval.Cp(-50), moverWhite = true))
        // The best move still loses.
        assertFalse(MistakePuzzles.onlyMove(Eval.Cp(-150), Eval.Cp(-600), moverWhite = true))
        // Black's view: +200 for White is losing for Black.
        assertTrue(MistakePuzzles.onlyMove(Eval.Cp(0), Eval.Cp(200), moverWhite = false))
        // Two mates are two answers; a mate vs nothing is one.
        assertFalse(MistakePuzzles.onlyMove(Eval.Mate(true, 1), Eval.Mate(true, 2), moverWhite = true))
        assertTrue(MistakePuzzles.onlyMove(Eval.Mate(true, 2), Eval.Cp(0), moverWhite = true))
        assertFalse(MistakePuzzles.onlyMove(Eval.Cp(0), null, moverWhite = true))
    }

    @Test
    fun mateEndsTheLine() {
        val s = searches(pe(Eval.Mate(true, 1), "a1a8", Eval.Cp(0)))
        assertEquals(listOf("a1a8"), MistakePuzzles.line("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", s))
    }

    @Test
    fun forkRunsUntilTheRookFalls() {
        val s = searches(
            pe(Eval.Cp(500), "b5c7", Eval.Cp(0)),
            pe(Eval.Cp(500), "e8d7"),
            pe(Eval.Cp(500), "c7a8", Eval.Cp(0)),
            pe(Eval.Cp(500), "d7c8"),
        )
        assertEquals(listOf("b5c7", "e8d7", "c7a8"), MistakePuzzles.line("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", s))
    }

    @Test
    fun noSecondOnlyMoveOrNoGainIsNoPuzzle() {
        // After Nc7+ Kd7, two moves win the rook back: no single answer, and nothing won yet.
        val s = searches(
            pe(Eval.Cp(500), "b5c7", Eval.Cp(0)),
            pe(Eval.Cp(500), "e8d7"),
            pe(Eval.Cp(500), "c7a8", Eval.Cp(450)),
        )
        assertNull(MistakePuzzles.line("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", s))
    }

    @Test
    fun materialIsFromTheUsersSide() {
        assertEquals(-2, MistakePuzzles.material("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", userWhite = true))
        assertEquals(2, MistakePuzzles.material("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", userWhite = false))
    }
}
