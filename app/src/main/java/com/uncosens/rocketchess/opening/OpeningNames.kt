package com.uncosens.rocketchess.opening

import com.uncosens.rocketchess.chess.GameLine

/** An opening name with its ECO code, e.g. "B90", "Sicilian Defense: Najdorf Variation". */
data class Opening(val eco: String, val name: String)

/** Opening names by position key, from lichess-org/chess-openings (made by scripts/opening_book.py). */
class OpeningNames(private val byKey: Map<Long, Opening>) {
    /** The name of the deepest named position of [line] up to [ply]. */
    fun of(line: GameLine, ply: Int): Opening? =
        (ply downTo 0).firstNotNullOfOrNull { byKey[OpeningBook.positionKey(line.positions[it].fen)] }

    companion object {
        /** Lines of "key in hex, ECO, name", tab separated. */
        fun parse(text: String) = OpeningNames(
            text.lineSequence().filter { it.isNotBlank() }.associate { line ->
                val (key, eco, name) = line.split('\t')
                java.lang.Long.parseUnsignedLong(key, 16) to Opening(eco, name)
            },
        )
    }
}
