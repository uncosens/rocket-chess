package com.uncosens.rocketchess.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PieceSetTest {
    @Test
    fun savedIdPicksSet() = assertEquals(PieceSet.MERIDA, PieceSet.fromId("merida"))

    @Test
    fun missingOrUnknownIdFallsBackToCburnett() {
        assertEquals(PieceSet.CBURNETT, PieceSet.fromId(null))
        assertEquals(PieceSet.CBURNETT, PieceSet.fromId("staunty"))
    }
}
