package com.uncosens.rocketchess.analysis

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PuzzlesTest {
    private val all = Puzzles.parse(
        """
        a	8/8/8/8/8/8/8/K6k w - - 0 1	a1a2 h1h2	900	fork
        b	8/8/8/8/8/8/8/K6k w - - 0 1	a1a2 h1h2	1450	mate mateIn2
        c	8/8/8/8/8/8/8/K6k w - - 0 1	a1a2 h1h2	1500	
        d	8/8/8/8/8/8/8/K6k w - - 0 1	a1a2 h1h2	1600	fork
        """.trimIndent(),
    )

    @Test
    fun parsesRows() {
        assertEquals(listOf("a1a2", "h1h2"), all[0].moves)
        assertEquals(emptyList<String>(), all[2].themes)
    }

    @Test
    fun picksTheThemeNearTheRating() {
        // d is 100 from 1500; a is too far for a themed pick.
        assertEquals("d", Puzzles.pick(all, 1500, "fork", emptySet(), Random(1))?.id)
        // No near fork left: any theme near the rating.
        val p = Puzzles.pick(all, 1500, "fork", setOf("d"), Random(1))?.id
        assert(p == "b" || p == "c")
        assertEquals("b", Puzzles.pick(all, 1500, "mateIn2", emptySet(), Random(1))?.id)
    }

    @Test
    fun convertsChessComToLichess() {
        assertEquals(1500, Puzzles.toLichess(1200))
        assertEquals(2600, Puzzles.toLichess(2600))
    }

    @Test
    fun puzzlePgnHasItsMoves() {
        val pgn = com.uncosens.rocketchess.chess.Setup.pgn("8/8/8/8/8/8/8/K6k b - - 0 1", listOf("h1h2", "a1a2"))
        assert(pgn.trimEnd().endsWith("1... Kh2 2. Ka2 *")) { pgn }
    }
}
