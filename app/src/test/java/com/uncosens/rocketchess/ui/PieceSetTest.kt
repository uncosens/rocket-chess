package com.uncosens.rocketchess.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PieceSetTest {
    @Test
    fun savedIdPicksSet() = assertEquals(PieceSet.MERIDA, PieceSet.fromId("merida"))

    @Test
    fun missingOrUnknownIdFallsBackToRocket() {
        assertEquals(PieceSet.ROCKET, PieceSet.fromId(null))
        assertEquals(PieceSet.ROCKET, PieceSet.fromId("staunty"))
        assertEquals(listOf(PieceSet.ROCKET, PieceSet.NOVA, PieceSet.ORBIT, PieceSet.CLASSIC), PieceSet.entries.take(4))
    }
}
