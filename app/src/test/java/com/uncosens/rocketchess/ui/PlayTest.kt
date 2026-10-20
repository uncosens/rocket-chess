package com.uncosens.rocketchess.ui

import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.chess.Finish
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayTest {
    @Test
    fun resignedGameBecomesAPgnForAnalysis() {
        val vm = PlayViewModel()
        vm.start(File("none"), EngineOptions(), white = true, elo = 1500)
        vm.play("e2e4")
        assertTrue(vm.engineTurn)
        vm.play("d2d4") // not the user's turn: ignored
        assertEquals(1, vm.line!!.plies.size)

        vm.resign()
        assertEquals("0-1", vm.result)
        val pgn = vm.pgn("You", "Stockfish 1500")
        val game = Pgn.parse(pgn)
        assertEquals("You", game.tags["White"])
        assertEquals(Finish.RESIGNED, game.finish())
        assertEquals(listOf("e4"), GameLine.from(game).plies.map { it.san })
    }

    @Test
    fun takeBackWhileTheEngineIsToMove() {
        val vm = PlayViewModel()
        vm.start(File("none"), EngineOptions(), white = true, elo = 1500)
        assertFalse(vm.canUndo)
        vm.play("e2e4")
        assertTrue(vm.canUndo)
        vm.undo()
        assertEquals(0, vm.line!!.plies.size)
        assertFalse(vm.engineTurn)
        // The board follows the take-back: the same move is legal again.
        vm.play("e2e4")
        assertEquals(listOf("e4"), vm.line!!.plies.map { it.san })
    }
}
