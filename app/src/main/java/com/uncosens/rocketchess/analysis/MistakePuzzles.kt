package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine

/** Numbers for turning a mistake into a puzzle. Win% values use [AnalysisPolicy.WIN_SLOPE]. */
object PuzzlePolicy {
    /** The best move beats the second by at least this much win% for the mover. */
    const val GAP = 15.0
    /** After the best move the mover keeps at least this win%: about even or better. */
    const val EVEN_FROM = 45.0
    /** At most this many user moves in a puzzle. */
    const val MAX_MOVES = 3
    /** The line must win at least this much material (pawn = 1) unless it mates. */
    const val MIN_GAIN = 2
    /** Where the line ends the user must have at least this win%: a clear win, not a trade back to even. */
    const val WIN_FROM = 60.0
    /** Or, when the user was below this win% before the opponent's mistake, back to [EVEN_FROM] is enough. */
    const val COMEBACK_BELOW = 30.0
    /** Search depth for checking a puzzle line, deeper than the game analysis. */
    const val DEPTH = 18
    /** Time limit per search; longer than the game analysis's, as the check runs in the background. */
    const val SEARCH_MS = 10_000L
}

/** Turns a mistake position into a 1–3 move puzzle line, or rejects it. */
object MistakePuzzles {
    /**
     * Only one move works: PV1 beats PV2 by [PuzzlePolicy.GAP] win% and PV2 lands in a worse result
     * (winning, even, losing), while PV1 keeps the mover at least about even. Two mates for the mover
     * are two answers, so no.
     */
    fun onlyMove(best: Eval, second: Eval?, moverWhite: Boolean): Boolean {
        if (second == null) return false
        if (best is Eval.Mate && second is Eval.Mate && best.whiteWins == moverWhite && second.whiteWins == moverWhite) return false
        val b = win(best, moverWhite)
        val s = win(second, moverWhite)
        return b >= PuzzlePolicy.EVEN_FROM && b - s >= PuzzlePolicy.GAP && outcome(b) > outcome(s)
    }

    /** No puzzle when the move is the only legal one or takes back what was just taken on that square. */
    fun trivial(fen: String, move: String, lastMove: String?, lastWasCapture: Boolean): Boolean =
        GameLine.legalMoves(fen).size == 1 || (lastWasCapture && lastMove != null && move.substring(2, 4) == lastMove.substring(2, 4))

    /**
     * The puzzle line from [fen]: user move, best reply, and so on, up to [PuzzlePolicy.MAX_MOVES] user moves.
     * The first move must be the only move ([onlyMove] on a fresh [search]). The line ends on a user move when
     * it mates, when the best reply leaves the user [PuzzlePolicy.MIN_GAIN] or more material up, when the next
     * user move is not the only move, or after the last move. Except for mate, the user must then have
     * [PuzzlePolicy.WIN_FROM] win% or more, or [PuzzlePolicy.EVEN_FROM] in a [comeback]; otherwise null.
     * A [comeback] is a position where the user was losing before the opponent's mistake.
     */
    fun line(fen: String, comeback: Boolean = false, search: (String) -> PositionEval): List<String>? {
        val userWhite = fen.split(' ')[1] == "w"
        val enough = if (comeback) PuzzlePolicy.EVEN_FROM else PuzzlePolicy.WIN_FROM
        val start = material(fen, userWhite)
        val line = mutableListOf<String>()
        var pos = fen
        repeat(PuzzlePolicy.MAX_MOVES) {
            val e = search(pos)
            val best = e.best ?: return null
            if (!onlyMove(e.eval, e.second, userWhite)) {
                // The user's last move stands as the end, when it already wins clearly (or holds a comeback).
                return if (line.isNotEmpty() && win(e.eval, userWhite) >= enough) line.dropLast(1) else null
            }
            line += best
            val after = GameLine.after(pos, best) ?: return null
            if (after.mate) return line
            val r = search(after.fen)
            val reply = r.best ?: return null
            val next = GameLine.after(after.fen, reply) ?: return null
            val winning = win(r.eval, userWhite) >= enough
            if (winning && material(next.fen, userWhite) - start >= PuzzlePolicy.MIN_GAIN) return line
            if (line.size == 2 * PuzzlePolicy.MAX_MOVES - 1) return if (winning) line else null
            line += reply
            pos = next.fen
        }
        return null
    }

    private fun win(e: Eval, moverWhite: Boolean) = whiteWinPercent(e).let { if (moverWhite) it else 100 - it }

    private val VALUES = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9)

    /** The user's material minus the opponent's, from a FEN. */
    fun material(fen: String, userWhite: Boolean): Int = fen.substringBefore(' ').sumOf { c ->
        val v = VALUES[c.lowercaseChar()] ?: 0
        if (c.isUpperCase() == userWhite) v else -v
    }
}
