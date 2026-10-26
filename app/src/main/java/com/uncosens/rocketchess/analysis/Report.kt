package com.uncosens.rocketchess.analysis

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.uncosens.rocketchess.chess.GameLine
import kotlin.math.abs
import kotlin.random.Random

enum class Axis { Tactics, Positional, Opening, Endgame, Defense }

/**
 * Numbers for My Report, checked on 100 analyzed rapid games of one player (2026-10): moving the
 * thresholds below by a few points moves each score by about 2. Win% values use [AnalysisPolicy.WIN_SLOPE].
 */
object ReportPolicy {
    /** No report below this many usable games. */
    const val MIN_GAMES = 25
    /** The report uses the user's most recently analyzed games, up to this many. */
    const val RECENT_GAMES = 100
    /** Runs shallower than this mix badly with the rest, so they are left out. */
    const val MIN_DEPTH = 12
    /** Share of positions that must have an eval. */
    const val MIN_COVERAGE = 0.9
    /** Endgame: at most this many pieces other than kings and pawns, both sides (Lichess's rule). */
    const val ENDGAME_PIECES = 6
    /** A position is critical when PV1 beats PV2 by this much win%. */
    const val CRITICAL_GAP = 10.0
    /** In a critical position, PV2 below this win% means only PV1 saves the game. */
    const val LOSING_BELOW = 40.0
    /** An opponent move that loses this much win% gives a chance to punish. */
    const val PUNISH_FROM = 10.0
    /** A non-critical move that loses this much and lets the reply hit the king is a king error. */
    const val KING_ERROR_FROM = 10.0
    /** One game adds at most this many moves' weight to an axis. */
    const val GAME_CAP = 30.0
    /** Pseudo moves that pull a small sample toward the overall accuracy. */
    const val SHRINK = 20.0
    const val BOOTSTRAP = 200
    /** The interval shown: 10th to 90th percentile. */
    const val BAND = 0.1

    /** Samples an axis needs before it gets a score. Endgame also needs [ENDGAME_GAMES] games. */
    fun needed(axis: Axis) = when (axis) {
        Axis.Opening, Axis.Positional, Axis.Endgame -> 100
        Axis.Tactics, Axis.Defense -> 30
    }
    const val ENDGAME_GAMES = 8
}

/** One user move, put in exactly one axis. [weight] is the chance size over its threshold for Tactics and Defense, else 1. */
data class ReportMove(val axis: Axis, val score: Double, val weight: Double, val chance: Boolean, val found: Boolean, val kingError: Boolean)

/** One axis of the report. [score] is null when [samples] is below [needed]; [low]..[high] is the shown interval. */
data class AxisScore(
    val axis: Axis,
    val score: Double?,
    val low: Double?,
    val high: Double?,
    val samples: Int,
    val needed: Int,
    val games: Int,
    val chances: Int,
    val found: Int,
)

data class Report(val axes: List<AxisScore>, val kingErrors: Int, val movesBeforeEndgame: Int)

object PlayerReport {
    /** The user's graded moves in one game, each in one axis. */
    fun moves(line: GameLine, evals: List<PositionEval?>, userWhite: Boolean): List<ReportMove> {
        val out = mutableListOf<ReportMove>()
        val middle = middlegameStart(line)
        for (i in line.plies.indices) {
            val pos = line.positions[i]
            val fields = pos.fen.split(' ')
            if ((fields[1] == "w") != userWhite) continue
            val before = evals.getOrNull(i) ?: continue
            val after = evals.getOrNull(i + 1) ?: continue
            fun win(e: Eval) = whiteWinPercent(e).let { if (userWhite) it else 100 - it }
            val loss = maxOf(0.0, win(before.eval) - win(after.eval))
            val score = moveScore(winLoss(before.eval, after.eval, userWhite, AnalysisPolicy.ACCURACY_SLOPE))
            val gap = before.second?.let { win(before.eval) - win(it) } ?: 0.0
            val prev = evals.getOrNull(i - 1)
            val punish = if (i > 0 && prev != null) maxOf(0.0, win(before.eval) - win(prev.eval)) else 0.0
            val found = line.plies[i].uci == before.best || loss < AnalysisPolicy.GOOD_BELOW
            // Taking back what was just taken, or the only legal move, is no real chance (a third of critical positions).
            val last = line.plies.getOrNull(i - 1)
            val trivial = (last != null && 'x' in last.san && before.best?.substring(2, 4) == last.to) || GameLine.legalMoves(pos.fen).size == 1
            fun add(axis: Axis, weight: Double = 1.0, chance: Boolean = false, kingError: Boolean = false) =
                out.add(ReportMove(axis, score, weight, chance, found, kingError))
            when {
                pieces(fields[0]) <= ReportPolicy.ENDGAME_PIECES -> add(Axis.Endgame)
                !trivial && gap >= ReportPolicy.CRITICAL_GAP && before.second != null && win(before.second) < ReportPolicy.LOSING_BELOW ->
                    add(Axis.Defense, gap / ReportPolicy.CRITICAL_GAP, chance = true)
                !trivial && gap >= ReportPolicy.CRITICAL_GAP -> add(Axis.Tactics, gap / ReportPolicy.CRITICAL_GAP, chance = true)
                pos.check != null -> add(Axis.Defense, chance = true)
                !trivial && punish >= ReportPolicy.PUNISH_FROM -> add(Axis.Tactics, punish / ReportPolicy.PUNISH_FROM, chance = true)
                loss >= ReportPolicy.KING_ERROR_FROM && hitsKing(line.positions[i + 1].fen, after, userWhite) ->
                    add(Axis.Defense, kingError = true)
                i < middle -> add(Axis.Opening)
                else -> add(Axis.Positional)
            }
        }
        return out
    }

    /**
     * Index of the first middlegame position, as Lichess's Divider (scalachess): at most 10 pieces
     * besides kings and pawns, fewer than 4 pieces on either side's back rank, or mixed armies.
     * Ported from scalachess, MIT, Copyright (c) 2012-2014 Thibault Duplessis (see THIRD_PARTY.md).
     */
    private fun middlegameStart(line: GameLine): Int = line.positions.indexOfFirst { p ->
        // grid[0] is rank 1; '.' is an empty square.
        val grid = p.fen.substringBefore(' ').split('/').reversed()
            .map { row -> row.flatMap { c -> if (c.isDigit()) List(c.digitToInt()) { '.' } else listOf(c) } }
        var mixed = 0
        for (y in 0..6) for (x in 0..6) {
            val region = listOf(grid[y][x], grid[y][x + 1], grid[y + 1][x], grid[y + 1][x + 1])
            mixed += mixedness(y + 1, region.count { it.isUpperCase() }, region.count { it.isLowerCase() })
        }
        pieces(p.fen.substringBefore(' ')) <= 10 || grid[0].count { it.isUpperCase() } < 4 || grid[7].count { it.isLowerCase() } < 4 || mixed > 150
    }.let { if (it < 0) line.positions.size else it }

    /** Lichess's score for a 2x2 region on row [y] (1 to 7) with [white] and [black] pieces. */
    private fun mixedness(y: Int, white: Int, black: Int): Int = when (white) {
        0 -> when (black) {
            1 -> 1 + y
            2 -> if (y < 6) 2 + (6 - y) else 0
            3, 4 -> if (y < 7) 3 + (7 - y) else 0
            else -> 0
        }
        1 -> when (black) {
            0 -> 1 + (8 - y)
            1 -> 5 + abs(4 - y)
            2 -> 4 + (7 - y)
            3 -> 5 + (7 - y)
            else -> 0
        }
        2 -> when (black) {
            0 -> if (y > 2) 2 + (y - 2) else 0
            1 -> 4 + (y - 1)
            2 -> 7
            else -> 0
        }
        3 -> when (black) {
            0 -> if (y > 1) 3 + (y - 1) else 0
            1 -> 5 + (y - 1)
            else -> 0
        }
        4 -> if (black == 0 && y > 1) 3 + (y - 1) else 0
        else -> 0
    }

    /** Pieces other than kings and pawns on the board part of a FEN. */
    private fun pieces(board: String) = board.count { it.isLetter() && it.lowercaseChar() != 'k' && it.lowercaseChar() != 'p' }

    /** True if the user is getting mated, or the engine's reply checks or captures next to the user's king. */
    private fun hitsKing(fen: String, after: PositionEval, userWhite: Boolean): Boolean {
        val e = after.eval
        if (e is Eval.Mate && e.whiteWins != userWhite) return true
        val reply = after.best ?: return false
        return runCatching {
            val board = Board().apply { loadFromFen(fen) }
            val user = board.sideToMove.flip()
            val king = board.getKingSquare(user)
            val to = Square.valueOf(reply.substring(2, 4).uppercase())
            val capture = board.getPiece(to) != Piece.NONE
            val near = abs(to.file.ordinal - king.file.ordinal) <= 1 && abs(to.rank.ordinal - king.rank.ordinal) <= 1
            val move = board.legalMoves().first { it.from.value().equals(reply.substring(0, 2), true) && it.to == to }
            board.doMove(move)
            board.isKingAttacked || (capture && near)
        }.getOrDefault(false)
    }

    /** The report from each usable game's moves. Null below [ReportPolicy.MIN_GAMES]. */
    fun build(games: List<List<ReportMove>>): Report? {
        if (games.size < ReportPolicy.MIN_GAMES) return null
        val all = games.flatten()
        val overall = all.map { it.score }.average().takeIf { !it.isNaN() } ?: return null
        val random = Random(1)
        val axes = Axis.entries.map { axis ->
            val per = games.map { m -> m.filter { it.axis == axis } }.filter { it.isNotEmpty() }
            val samples = per.sumOf { it.size }
            val needed = ReportPolicy.needed(axis)
            val enough = samples >= needed && (axis != Axis.Endgame || per.size >= ReportPolicy.ENDGAME_GAMES)
            val chances = per.sumOf { m -> m.count { it.chance } }
            val found = per.sumOf { m -> m.count { it.chance && it.found } }
            if (!enough) return@map AxisScore(axis, null, null, null, samples, needed, per.size, chances, found)
            val runs = List(ReportPolicy.BOOTSTRAP) { score(List(per.size) { per[random.nextInt(per.size)] }, overall) }.sorted()
            val cut = (ReportPolicy.BAND * runs.size).toInt()
            AxisScore(axis, score(per, overall), runs[cut], runs[runs.size - 1 - cut], samples, needed, per.size, chances, found)
        }
        val beforeEnd = all.count { it.axis != Axis.Endgame }
        return Report(axes, all.count { it.kingError }, beforeEnd)
    }

    /** Weighted mean score, each game capped at [ReportPolicy.GAME_CAP] moves, pulled toward [overall]. */
    private fun score(per: List<List<ReportMove>>, overall: Double): Double {
        var sum = 0.0
        var weights = 0.0
        var n = 0.0
        for (m in per) {
            val cap = minOf(1.0, ReportPolicy.GAME_CAP / m.size)
            for (x in m) {
                sum += cap * x.weight * x.score
                weights += cap * x.weight
            }
            n += cap * m.size
        }
        val mean = sum / weights
        return (n * mean + ReportPolicy.SHRINK * overall) / (n + ReportPolicy.SHRINK)
    }
}
