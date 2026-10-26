package com.uncosens.rocketchess.analysis

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A Lichess puzzle (CC0). [fen] is the position before the opponent's move; [moves] start with that
 * move, then the solution, user and opponent in turn.
 */
data class Puzzle(val id: String, val fen: String, val moves: List<String>, val rating: Int, val themes: List<String>)

/** The puzzle set made by scripts/puzzle_set.py, sorted by rating. */
object Puzzles {
    /** Rows of id, FEN, moves, rating and themes, tab separated. */
    fun parse(text: String): List<Puzzle> = text.lineSequence().filter { it.isNotBlank() }.map { line ->
        val f = line.split('\t')
        Puzzle(f[0], f[1], f[2].split(' '), f[3].toInt(), f.getOrNull(4)?.split(' ')?.filter { it.isNotEmpty() }.orEmpty())
    }.toList()

    /**
     * A rough Lichess rating for a Chess.com rating: about 350 higher at 950, the same from 2500 up.
     * No site publishes a conversion table, so this is an estimate.
     */
    fun toLichess(chessCom: Int): Int = if (chessCom >= SAME_FROM) chessCom else (SAME_FROM - (SAME_FROM - chessCom) / GAP).roundToInt()

    private const val SAME_FROM = 2500
    private const val GAP = 1.3

    /** Widening rating windows to look for a puzzle in. */
    private val WINDOWS = listOf(100, 200, 400, 10_000)

    /**
     * A random puzzle near [rating] with [theme] (any theme when null or none is found), not in [skip].
     * [all] must be sorted by rating. The closest rating window that has one wins; a theme is only
     * looked for within 400 points.
     */
    fun pick(all: List<Puzzle>, rating: Int, theme: String?, skip: Set<String>, random: Random): Puzzle? {
        for (themed in listOf(theme, null).distinct()) {
            for (w in WINDOWS) {
                if (themed != null && w > 400) break
                val near = between(all, rating - w, rating + w).filter { it.id !in skip && (themed == null || themed in it.themes) }
                if (near.isNotEmpty()) return near.random(random)
            }
        }
        return null
    }

    /** Puzzles rated [low] to [high], from a list sorted by rating. */
    private fun between(all: List<Puzzle>, low: Int, high: Int): List<Puzzle> {
        val from = all.binarySearch { if (it.rating < low) -1 else 1 }.let { -it - 1 }
        val to = all.binarySearch { if (it.rating <= high) -1 else 1 }.let { -it - 1 }
        return all.subList(from, to)
    }
}
