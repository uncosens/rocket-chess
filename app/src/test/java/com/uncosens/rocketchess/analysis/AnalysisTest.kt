package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.engine.Uci
import com.uncosens.rocketchess.engine.UciInfo
import com.uncosens.rocketchess.engine.UciScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(MoveClass.Good, c(-3.0))
        assertEquals(MoveClass.Good, c(0.0))
        assertEquals(MoveClass.Good, c(1.999))
        assertEquals(MoveClass.Fine, c(2.0))
        assertEquals(MoveClass.Fine, c(4.999))
        assertEquals(MoveClass.Weak, c(5.0))
        assertEquals(MoveClass.Weak, c(9.999))
        assertEquals(MoveClass.Mistake, c(10.0))
        assertEquals(MoveClass.Mistake, c(19.999))
        assertEquals(MoveClass.Blunder, c(20.0))
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

    @Test
    fun readsAndClampsEngineOptions() {
        // Nothing saved: the defaults.
        assertEquals(EngineOptions(4, 32, 14), EngineOptions.read(cores = 8) { _, d -> d })
        val saved = mapOf("threads" to 9, "hash_mb" to 100, "depth" to 3)
        assertEquals(EngineOptions(4, 128, 10), EngineOptions.read(cores = 8) { k, d -> saved[k] ?: d })
        // Threads stop at the core count; hash snaps to the nearest choice.
        assertEquals(EngineOptions(1, 64, 20), EngineOptions(threads = 2, hashMb = 70, depth = 25).clamped(cores = 1))
        assertEquals(EngineOptions(1, 16, 14), EngineOptions(threads = 0, hashMb = 1).clamped(cores = 4))
        assertEquals(3, EngineOptions.maxThreads(3))
    }

    @Test
    fun enginesFollowCoresAndMemory() {
        fun n(cores: Int, totalMb: Long, availMb: Long, lowRam: Boolean = false) = EngineBudget.engines(cores, totalMb, availMb, lowRam, 32)
        assertEquals(4, n(4, 7_242, 2_065)) // Galaxy S23+ (8 GB) as measured
        assertEquals(2, n(2, 7_242, 2_065)) // the user's core setting wins
        assertEquals(2, n(4, 5_500, 3_000)) // 6 GB device
        assertEquals(1, n(4, 3_700, 2_000)) // 4 GB device
        assertEquals(2, n(4, 7_242, 1_200)) // little free memory right now
        assertEquals(1, n(4, 7_242, 300)) // always at least one
        assertEquals(1, n(4, 16_000, 8_000, lowRam = true))
        assertEquals(16, EngineBudget.hashShare(EngineOptions(hashMb = 32), 4))
        assertEquals(32, EngineBudget.hashShare(EngineOptions(hashMb = 128), 4))
    }

    @Test
    fun savedAnalysisRoundTrip() {
        val settings = AnalysisSettings("Stockfish 19", depth = 15, multiPv = 2, threads = 3, hashMb = 64)
        val evals = listOf(
            PositionEval(Eval.Cp(35), "e2e4", Eval.Cp(-20), 15),
            null,
            PositionEval(Eval.Mate(whiteWins = false, moves = 3), "e7e8q", null, 14),
            PositionEval(Eval.Mate(whiteWins = true, moves = 0), null, null, 0),
        )
        val text = SavedAnalysis.encode(settings, evals)
        assertEquals("Stockfish 19\t15\t2\t3\t64\nc35,e2e4,c-20,15;;m-3,e7e8q,,14;m+0,,,0", text)
        assertEquals(settings to evals, SavedAnalysis.decode(text))
        assertNull(SavedAnalysis.decode("broken"))
    }

    private fun sacrifice(pgn: String) = GameLine.from(Pgn.parse(pgn)).plies.last().sacrifice

    @Test
    fun sacrificeIsLosingAPieceOnItsSquare() {
        // Greek gift: Bxh7+ Kxh7 gives a bishop for a pawn.
        val greek = "[FEN \"r1bq1rk1/pppn1ppp/4p3/3pP3/1b1P4/2NB1N2/PPP2PPP/R1BQK2R w KQ - 0 1\"]\n\n1. Bxh7+ *"
        assertTrue(sacrifice(greek))
        assertFalse(sacrifice("1. e4 *"))
        // King's Gambit gives only a pawn.
        assertFalse(sacrifice("1. e4 e5 2. f4 *"))
        // Nd5 hangs the knight to cxd5, unless Ba4 pins that pawn to its king.
        assertTrue(sacrifice("[FEN \"4k3/8/2p5/8/8/4N3/8/4K3 w - - 0 1\"]\n\n1. Nd5 *"))
        assertFalse(sacrifice("[FEN \"4k3/8/2p5/8/B7/4N3/8/4K3 w - - 0 1\"]\n\n1. Nd5 *"))
        // Exchange sacrifice: Rxc6 bxc6 gives a rook for a knight.
        assertTrue(sacrifice("[FEN \"4k3/1p6/2n5/8/8/8/8/2R1K3 w - - 0 1\"]\n\n1. Rxc6 *"))
        // A defended pawn taken by a pawn is a fair trade.
        assertFalse(sacrifice("1. e4 d5 2. exd5 *"))
    }

    @Test
    fun brilliantIsASoundSacrificeWhenNotAlreadyWinning() {
        val fen = "r1bq1rk1/pppn1ppp/4p3/3pP3/1b1P4/2NB1N2/PPP2PPP/R1BQK2R w KQ - 0 1"
        val line = GameLine.from(Pgn.parse("[FEN \"$fen\"]\n\n1. Bxh7+ *"))
        fun grade(before: Int, after: Int, best: String = "d3h7") = Analyzer.grade(
            line, listOf(PositionEval(Eval.Cp(before), best, null, 14), PositionEval(Eval.Cp(after), "g8h7", null, 14)), 0,
        )!!.cls
        assertEquals(MoveClass.Brilliant, grade(150, 140))
        // About 93% to win already: just Best.
        assertEquals(MoveClass.Best, grade(700, 690))
        // Still losing after it (about 25%): just Best.
        assertEquals(MoveClass.Best, grade(-290, -300))
        // Just above even after it: Brilliant.
        assertEquals(MoveClass.Brilliant, grade(15, 5))
        // Not the engine's choice.
        assertEquals(MoveClass.Good, grade(150, 140, best = "f3g5"))
        // The engine's choice, but the next position looks 2+ points worse: no Brilliant.
        assertEquals(MoveClass.Best, grade(150, 100))
    }

    @Test
    fun accuracyIsMeanGradeScore() {
        // White plays the best move, then loses about 10 win% (a Mistake); Black plays the best move.
        val line = GameLine.from(Pgn.parse("1. e4 e5 2. Qh5 *"))
        val evals = listOf(
            PositionEval(Eval.Cp(30), "e2e4", null, 14),
            PositionEval(Eval.Cp(30), "e7e5", null, 14),
            PositionEval(Eval.Cp(30), "g1f3", null, 14),
            PositionEval(Eval.Cp(-81), "b8c6", null, 14),
        )
        assertEquals(MoveClass.Mistake, Analyzer.grade(line, evals, 2)!!.cls)
        assertEquals((100.0 + 11.0) / 2, Analyzer.accuracy(line, evals, white = true)!!, 0.001)
        assertEquals(100.0, Analyzer.accuracy(line, evals, white = false)!!, 0.001)
        assertNull(Analyzer.accuracy(line, listOf(evals[0]), white = true))
        // A Blunder scores below 0, but a side's accuracy stops at 0.
        val one = GameLine.from(Pgn.parse("1. d4 *"))
        val blunder = listOf(PositionEval(Eval.Cp(30), "e2e4", null, 14), PositionEval(Eval.Cp(-900), "e7e5", null, 14))
        assertEquals(MoveClass.Blunder, Analyzer.grade(one, blunder, 0)!!.cls)
        assertEquals(0.0, Analyzer.accuracy(one, blunder, white = true)!!, 0.001)
    }
}
