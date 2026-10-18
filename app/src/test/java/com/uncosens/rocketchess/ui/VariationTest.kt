package com.uncosens.rocketchess.ui

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariationTest {
    private val game = GameLine.from(Pgn.parse("1. e4 e5 2. Nf3 Nc6 *"))

    @Test
    fun ownMoveStartsVariation() {
        val v = Variation.of(game, game.play(2, "f2f4")!!)!!
        assertEquals(2, v.branch) // shares 1. e4 e5
        assertEquals(listOf("e4", "e5", "f4"), v.line.plies.map { it.san })
    }

    @Test
    fun gameMoveIsNoVariation() {
        assertNull(Variation.of(game, game.play(2, "g1f3")!!))
        // Playing the game's move back from inside a variation also ends it.
        val v = Variation.of(game, game.play(1, "c7c5")!!)!!
        assertNull(Variation.of(game, v.line.play(1, "e7e5")!!))
    }
}
