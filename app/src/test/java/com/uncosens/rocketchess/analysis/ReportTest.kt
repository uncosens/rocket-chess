package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    private fun line(pgn: String) = GameLine.from(Pgn.parse(pgn))
    private fun pe(cp: Int, best: String? = null, second: Int? = null) = PositionEval(Eval.Cp(cp), best, second?.let { Eval.Cp(it) }, 14)

    @Test
    fun quietEarlyMovesAreOpening() {
        val l = line("1. e4 e5 2. Nf3 Nc6 *")
        val moves = PlayerReport.moves(l, List(5) { pe(20, second = 10) }, userWhite = true)
        assertEquals(listOf(Axis.Opening, Axis.Opening), moves.map { it.axis })
        assertTrue(moves.all { it.score > 99 })
    }

    @Test
    fun criticalPositionIsTacticsOrDefense() {
        val l = line("1. e4 e5 *")
        // PV2 still about even: a chance to win more, found by playing PV1.
        val tactic = PlayerReport.moves(l, listOf(pe(300, "e2e4", second = 0), pe(300), pe(300)), userWhite = true).single()
        assertEquals(Axis.Tactics, tactic.axis)
        assertTrue(tactic.chance && tactic.found)
        // PV2 loses: only PV1 holds.
        val defense = PlayerReport.moves(l, listOf(pe(0, "d2d4", second = -600), pe(-600), pe(-600)), userWhite = true).single()
        assertEquals(Axis.Defense, defense.axis)
        assertFalse(defense.found)
    }

    @Test
    fun recaptureIsNoChance() {
        val l = line("1. e4 d5 2. exd5 Qxd5 3. Nc3 *")
        // 2...Qxd5 takes back on d5: a big gap, but not a tactic.
        val evals = listOf(pe(20), pe(20), pe(60), pe(-300, "d8d5", second = 300), pe(60), pe(60))
        val m = PlayerReport.moves(l, evals, userWhite = false)
        assertEquals(listOf(Axis.Opening, Axis.Opening), m.map { it.axis })
    }

    @Test
    fun opponentErrorIsAChanceToPunish() {
        val l = line("1. e4 e5 2. Nf3 *")
        // Black's 1...e5 gives away 400 cp; White's 2. Nf3 is the punish chance.
        val m = PlayerReport.moves(l, listOf(pe(0, second = -5), pe(0, second = -5), pe(400, second = 390), pe(400)), userWhite = true)
        assertEquals(listOf(Axis.Opening, Axis.Tactics), m.map { it.axis })
    }

    @Test
    fun moveThatAllowsMateIsAKingError() {
        val l = line("1. f3 e5 2. g4 Qh4# 0-1")
        val evals = listOf(pe(30, second = 20), pe(-50, second = -60), pe(-50, second = -60), PositionEval(Eval.Mate(false, 1), "d8h4", null, 14), PositionEval(Eval.Mate(false, 0), null, null, 14))
        val m = PlayerReport.moves(l, evals, userWhite = true)
        assertEquals(listOf(Axis.Opening, Axis.Defense), m.map { it.axis })
        assertTrue(m[1].kingError)
    }

    @Test
    fun thinBackRankStartsTheMiddlegame() {
        // White's back rank holds 3 pieces: Lichess calls this the middlegame, whatever the move number.
        val l = line("[FEN \"rnbqkbnr/pppppppp/8/8/8/2NBBN2/PPPQPPPP/R3K2R w KQkq - 0 5\"]\n\n5. Rb1 *")
        val m = PlayerReport.moves(l, listOf(pe(20, second = 10), pe(20)), userWhite = true).single()
        assertEquals(Axis.Positional, m.axis)
    }

    @Test
    fun fewPiecesIsEndgame() {
        val l = line("[FEN \"8/5k2/8/8/8/8/R4K2/8 w - - 0 40\"]\n\n40. Ra7+ *")
        val m = PlayerReport.moves(l, listOf(pe(800, second = 790), pe(800)), userWhite = true).single()
        assertEquals(Axis.Endgame, m.axis)
    }

    @Test
    fun reportNeedsEnoughGamesAndSamples() {
        val game = true to List(10) { ReportMove(Axis.Opening, 80.0, 1.0, chance = false, found = false, kingError = false) }
        assertNull(PlayerReport.build(List(ReportPolicy.MIN_GAMES - 1) { game }))
        val r = PlayerReport.build(List(ReportPolicy.MIN_GAMES) { game })
        assertNotNull(r)
        val opening = r!!.axes.first { it.axis == Axis.Opening }
        assertEquals(80.0, opening.score!!, 0.01)
        assertTrue(opening.low!! <= opening.score!! && opening.score!! <= opening.high!!)
        // No tactics moves at all: not scored, not zero.
        assertNull(r.axes.first { it.axis == Axis.Tactics }.score)
    }
}
