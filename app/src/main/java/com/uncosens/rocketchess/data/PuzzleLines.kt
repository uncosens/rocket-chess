package com.uncosens.rocketchess.data

import android.content.Context
import java.io.File

/**
 * Checked mistake puzzles, kept so each position is searched once. Rows: mistake key, then the
 * line's moves, or "-" when the position makes no puzzle.
 */
object PuzzleLines {
    private fun file(c: Context) = File(c.filesDir, "puzzle_lines.tsv")

    /** Each checked key with its line; an empty line means no puzzle. */
    fun load(c: Context): Map<String, List<String>> = runCatching {
        file(c).readLines().mapNotNull { row ->
            val (key, moves) = row.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
            key to if (moves == NONE) emptyList() else moves.split(' ')
        }.toMap()
    }.getOrDefault(emptyMap())

    @Synchronized
    fun save(c: Context, key: String, line: List<String>?) {
        file(c).appendText("$key\t${line?.joinToString(" ") ?: NONE}\n")
    }

    private const val NONE = "-"
}
