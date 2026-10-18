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

enum class MoveClass { Brilliant, Best, Excellent, Good, Inaccuracy, Mistake, Blunder }

/** All analysis numbers live here so UI code never repeats them. Not the official Lichess grading. */
object AnalysisPolicy {
    const val DEPTH = 14
    const val MULTI_PV = 2
    // Defaults for the user settings in EngineOptions.
    // CPU cores: engines run at once for a full analysis, one thread each.
    const val THREADS = 4
    const val HASH_MB = 32

    /** Upper bounds (exclusive) of win% loss for each class after Best. */
    const val EXCELLENT_BELOW = 2.0
    const val GOOD_BELOW = 5.0
    const val INACCURACY_BELOW = 10.0
    const val MISTAKE_BELOW = 15.0
}

/** White's winning chance in percent. Lichess-style curve on centipawns; mate is 0 or 100. */
fun whiteWinPercent(eval: Eval): Double = when (eval) {
    is Eval.Cp -> 50 + 50 * (2 / (1 + exp(-0.00368208 * eval.cp)) - 1)
    is Eval.Mate -> if (eval.whiteWins) 100.0 else 0.0
}

/** Win% the mover gave away: before the move vs after it, both from the mover's view. Can be negative. */
fun winLoss(before: Eval, after: Eval, whiteMoved: Boolean): Double {
    val b = whiteWinPercent(before)
    val a = whiteWinPercent(after)
    return if (whiteMoved) b - a else a - b
}

/** En Croissant's centipawns for grading: the mover's view, mate as ±1000, clamped to ±1000. */
private fun ecCp(eval: Eval, white: Boolean): Int {
    val cp = when (eval) {
        is Eval.Cp -> eval.cp
        is Eval.Mate -> if (eval.whiteWins) 1000 else -1000
    }
    return (if (white) cp else -cp).coerceIn(-1000, 1000)
}

private fun ecWin(cp: Int) = whiteWinPercent(Eval.Cp(cp))

/**
 * En Croissant's "!!" (src/utils/score.ts, getAnnotation): the move loses at most 5 win%, PV2 was more
 * than 10 win% worse than PV1 (the move was the only good one), the move is PV1, and it is a sacrifice.
 */
fun isBrilliant(before: PositionEval, after: Eval, playedUci: String, sacrifice: Boolean, whiteMoved: Boolean): Boolean {
    if (ecWin(ecCp(before.eval, whiteMoved)) - ecWin(ecCp(after, whiteMoved)) > 5) return false
    val second = before.second ?: return false
    val onlyMove = ecWin(ecCp(before.eval, whiteMoved)) - ecWin(ecCp(second, whiteMoved)) > 10
    return onlyMove && playedUci == before.best && sacrifice
}

/** Lichess-style accuracy of one move from its win% loss: 100 means nothing was lost. */
fun moveAccuracy(loss: Double): Double = (103.1668 * exp(-0.04354 * loss.coerceAtLeast(0.0)) - 3.1669).coerceIn(0.0, 100.0)

/**
 * Best when the played move is the engine's first choice (PV1). Otherwise by win% loss.
 * A negative loss (the position after looks better than before) happens when the deeper look
 * at the next position finds more; it is graded as no loss.
 */
fun classify(playedUci: String, bestUci: String?, loss: Double): MoveClass = when {
    playedUci == bestUci -> MoveClass.Best
    loss < AnalysisPolicy.EXCELLENT_BELOW -> MoveClass.Excellent
    loss < AnalysisPolicy.GOOD_BELOW -> MoveClass.Good
    loss < AnalysisPolicy.INACCURACY_BELOW -> MoveClass.Inaccuracy
    loss < AnalysisPolicy.MISTAKE_BELOW -> MoveClass.Mistake
    else -> MoveClass.Blunder
}
