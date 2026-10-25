package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine

/**
 * A Mistake or Blunder the user made, as a puzzle: find a better move in [fen].
 * [answers] are the engine's first choice, plus its second when that loses less than Good's line.
 * [lastMove] is the opponent's move that led to [fen], as (from, to).
 */
data class Mistake(
    val fen: String,
    val played: String,
    val answers: List<String>,
    val cls: MoveClass,
    val lastMove: Pair<String, String>?,
) {
    /** Same position and same wrong move: one puzzle, even if the game is saved twice. */
    val key: String get() = "$fen|$played"
}

object Mistakes {
    /** The user's Mistakes and Blunders in one analyzed game, in game order. */
    fun of(line: GameLine, evals: List<PositionEval?>, userWhite: Boolean): List<Mistake> =
        line.plies.indices.mapNotNull { i ->
            if ((line.positions[i].fen.split(' ')[1] == "w") != userWhite) return@mapNotNull null
            val cls = Analyzer.grade(line, evals, i)?.cls ?: return@mapNotNull null
            if (cls != MoveClass.Mistake && cls != MoveClass.Blunder) return@mapNotNull null
            val before = evals[i] ?: return@mapNotNull null
            val best = before.best ?: return@mapNotNull null
            val second = before.secondBest?.takeIf { before.second != null && winLoss(before.eval, before.second, userWhite) < AnalysisPolicy.GOOD_BELOW }
            val last = line.plies.getOrNull(i - 1)
            Mistake(line.positions[i].fen, line.plies[i].uci, listOfNotNull(best, second).filter { it != line.plies[i].uci }, cls, last?.let { it.from to it.to })
        }
}
