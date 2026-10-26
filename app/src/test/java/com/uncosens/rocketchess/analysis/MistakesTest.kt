package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakesTest {
    private fun line(pgn: String) = GameLine.from(Pgn.parse(pgn))
    private fun pe(cp: Int, best: String? = null, second: Int? = null, secondBest: String? = null) =
        PositionEval(Eval.Cp(cp), best, second?.let { Eval.Cp(it) }, 14, secondBest)

    @Test
    fun onlyTheUsersMistakesAndBlunders() {
        val l = line("1. e4 e5 2. Qh5 Nc6 3. Qxf7+ *")
        // White's 2. Qh5 drops 330 cp (Blunder); Black's 2...Nc6 drops a lot too but is not the user's move.
        val evals = listOf(pe(30), pe(30), pe(30, "g1f3", -200, "b1c3"), pe(-300), pe(500), pe(500))
        val m = Mistakes.of(l, evals, userWhite = true)
        assertEquals(1, m.size)
        assertEquals("d1h5", m[0].played)
        assertEquals(MoveClass.Blunder, m[0].cls)
        assertEquals(listOf("g1f3"), m[0].answers)
        assertEquals("e7" to "e5", m[0].lastMove)
    }

    @Test
    fun closeSecondChoiceIsNoPuzzle() {
        val l = line("1. e4 e5 2. Qh5 *")
        // Nf3 and Nc3 are both fine, so the position has no single answer.
        val evals = listOf(pe(30), pe(30), pe(30, "g1f3", 20, "b1c3"), pe(-300))
        assertTrue(Mistakes.of(l, evals, userWhite = true).isEmpty())
    }

    @Test
    fun recaptureIsNoPuzzle() {
        val l = line("1. e4 d5 2. exd5 a6 *")
        // Taking back on d5 was the only good move, but it is too easy.
        val evals = listOf(pe(30), pe(30), pe(30), pe(0, "d8d5", 300, "g8f6"), pe(300))
        assertTrue(Mistakes.of(l, evals, userWhite = false).isEmpty())
    }

    @Test
    fun smallLossIsNoMistake() {
        val l = line("1. e4 e5 *")
        assertTrue(Mistakes.of(l, listOf(pe(30, "d2d4"), pe(20), pe(20)), userWhite = true).isEmpty())
    }
    private val quiet = pe(0)

    @Test
    fun missedOrAllowedMateIsAMateTheme() {
        assertEquals(listOf("mateIn2"), Mistakes.themes(START, "e2e4", Eval.Mate(true, 2), START, quiet, userWhite = true))
        // The reply mates the user in one.
        val after = PositionEval(Eval.Mate(false, 1), "d8h4", null, 14)
        assertEquals(listOf("mateIn1"), Mistakes.themes(START, "e2e4", Eval.Cp(0), START, after, userWhite = true))
    }

    @Test
    fun forkAndFreePiece() {
        assertEquals(listOf("fork"), Mistakes.themes("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", "b5c7", Eval.Cp(500), START, quiet, userWhite = true))
        assertEquals(listOf("hangingPiece"), Mistakes.themes("4k3/8/8/3n4/8/8/8/3QK3 w - - 0 1", "d1d5", Eval.Cp(300), START, quiet, userWhite = true))
        // A defended knight is no free piece.
        assertEquals(emptyList<String>(), Mistakes.themes("4k3/8/4p3/3n4/8/8/8/3QK3 w - - 0 1", "d1d5", Eval.Cp(0), START, quiet, userWhite = true))
    }

    private companion object {
        const val START = GameLine.START_FEN
    }
}
