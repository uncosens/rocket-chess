package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.engine.UciScore
import kotlin.math.exp

/** A score from White's view. Mate keeps who wins and the distance in moves (0 = already mated). */
sealed interface Eval {
    data class Cp(val cp: Int) : Eval
    data class Mate(val whiteWins: Boolean, val moves: Int) : Eval

    companion object {
        /** Converts an engine score (side to move's view) to White's view. */
        fun fromUci(score: UciScore, whiteToMove: Boolean): Eval = when (score) {
            is UciScore.Cp -> Cp(if (whiteToMove) score.cp else -score.cp)
            // mate > 0: side to move mates. mate <= 0: side to move is mated.
            is UciScore.Mate -> Mate(whiteWins = (score.moves > 0) == whiteToMove, moves = kotlin.math.abs(score.moves))
        }
    }
}

enum class MoveClass { Brilliant, Great, Best, Book, Good, Fine, Weak, Mistake, Blunder }

/** All analysis numbers live here so UI code never repeats them. Not the official Lichess grading. */
object AnalysisPolicy {
    const val DEPTH = 14
    const val MULTI_PV = 2
    // Defaults for the user settings in EngineOptions.
    // CPU cores: engines run at once for a full analysis, one thread each.
    const val THREADS = 4
    const val HASH_MB = 32

    /** Upper bounds (exclusive) of win% loss for each class after Best. */
    const val GOOD_BELOW = 2.0
    const val FINE_BELOW = 5.0
    const val WEAK_BELOW = 10.0
    // Chess.com's lines: a blunder loses 20% or more.
    const val MISTAKE_BELOW = 20.0

    /** At or above this win% before the move the mover is already winning, so no Brilliant. */
    const val WINNING_FROM = 90.0

    /** A Brilliant must leave the mover at least this win% after the move. */
    const val SOUND_FROM = 50.0

    /** A Great move beats the engine's second choice by at least this win% for the mover. */
    const val GREAT_GAP = 10.0

    /** Mover's win% from [EVEN_FROM] to [EVEN_UNTIL] counts as about even; below is losing, above is winning. */
    const val EVEN_FROM = 40.0
    const val EVEN_UNTIL = 60.0

    /** No Great before this move number: early moves are mostly book moves. */
    const val GREAT_FROM_MOVE = 6

    /** Win% curve slope for grades and the eval graph (Lichess). */
    const val WIN_SLOPE = 0.00368208
    // Accuracy uses a flatter curve and a move score of 100 - DROP * (1 - exp(-DECAY * loss)).
    // Fitted to Chess.com's accuracy on 500 games of t4ph3s (2026-10): 2.9 points off on average, was 4.4.
    const val ACCURACY_SLOPE = 0.00177
    const val ACCURACY_DROP = 208.0
    const val ACCURACY_DECAY = 0.059
}

/** White's winning chance in percent. Lichess-style curve on centipawns; mate is 0 or 100. */
fun whiteWinPercent(eval: Eval, slope: Double = AnalysisPolicy.WIN_SLOPE): Double = when (eval) {
    is Eval.Cp -> 50 + 50 * (2 / (1 + exp(-slope * eval.cp)) - 1)
    is Eval.Mate -> if (eval.whiteWins) 100.0 else 0.0
}

/** Win% the mover gave away: before the move vs after it, both from the mover's view. Can be negative. */
fun winLoss(before: Eval, after: Eval, whiteMoved: Boolean, slope: Double = AnalysisPolicy.WIN_SLOPE): Double {
    val b = whiteWinPercent(before, slope)
    val a = whiteWinPercent(after, slope)
    return if (whiteMoved) b - a else a - b
}

/**
 * Our Brilliant rule: a piece sacrifice that is the engine's first choice, loses less than Good's line,
 * was played when the mover was not already winning, and leaves the mover at least even.
 * [moverWinBefore] is the mover's win% before the move; minus [loss] it is the win% after.
 */
fun isBrilliant(playedUci: String, bestUci: String?, sacrifice: Boolean, loss: Double, moverWinBefore: Double): Boolean =
    sacrifice && playedUci == bestUci && loss < AnalysisPolicy.GOOD_BELOW &&
        moverWinBefore < AnalysisPolicy.WINNING_FROM && moverWinBefore - loss >= AnalysisPolicy.SOUND_FROM

/** 0 losing, 1 about even, 2 winning, from the mover's win%. */
private fun outcome(moverWin: Double): Int = when {
    moverWin < AnalysisPolicy.EVEN_FROM -> 0
    moverWin <= AnalysisPolicy.EVEN_UNTIL -> 1
    else -> 2
}

/**
 * Our Great rule: the engine's first choice, far better than its second choice, and it changes the result
 * (losing to even, or even to winning). A plain recapture or a move in the first 5 moves is never Great.
 * [moverWinBest] and [moverWinSecond] are the mover's win% for PV1 and PV2; [moveNumber] is the FEN move number.
 */
fun isGreat(playedUci: String, bestUci: String?, moverWinBest: Double, moverWinSecond: Double?, recapture: Boolean, moveNumber: Int): Boolean =
    !recapture && moveNumber >= AnalysisPolicy.GREAT_FROM_MOVE && playedUci == bestUci && moverWinSecond != null &&
        moverWinBest - moverWinSecond >= AnalysisPolicy.GREAT_GAP && outcome(moverWinBest) > outcome(moverWinSecond)

/**
 * Points a move adds to its side's accuracy, which is the mean of these. [loss] is win% lost on the
 * accuracy curve ([AnalysisPolicy.ACCURACY_SLOPE]); no loss is 100, a big blunder goes below 0.
 */
fun moveScore(loss: Double): Double =
    100 - AnalysisPolicy.ACCURACY_DROP * (1 - exp(-AnalysisPolicy.ACCURACY_DECAY * maxOf(0.0, loss)))

/**
 * Book when the move and all moves before it are book moves (OpeningBook.bookPlies), unless the engine
 * grades it Weak or worse. Accuracy does not change: it uses the win% loss, not the grade.
 */
fun bookGrade(cls: MoveClass, inBook: Boolean): MoveClass =
    if (inBook && cls != MoveClass.Weak && cls != MoveClass.Mistake && cls != MoveClass.Blunder) MoveClass.Book else cls

/**
 * Best when the played move is the engine's first choice (PV1). Otherwise by win% loss.
 * A negative loss (the position after looks better than before) happens when the deeper look
 * at the next position finds more; it is graded as no loss.
 */
fun classify(playedUci: String, bestUci: String?, loss: Double): MoveClass = when {
    playedUci == bestUci -> MoveClass.Best
    loss < AnalysisPolicy.GOOD_BELOW -> MoveClass.Good
    loss < AnalysisPolicy.FINE_BELOW -> MoveClass.Fine
    loss < AnalysisPolicy.WEAK_BELOW -> MoveClass.Weak
    loss < AnalysisPolicy.MISTAKE_BELOW -> MoveClass.Mistake
    else -> MoveClass.Blunder
}
