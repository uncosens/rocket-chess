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
    fun gameFromASetUpPosition() {
        // Black to move in a set-up position; the user plays Black.
        val fen = "4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 1"
        val vm = PlayViewModel()
        vm.prepare(fen)
        vm.start(File("none"), EngineOptions(), white = false, elo = 1500)
        assertFalse(vm.engineTurn)
        vm.play("e7e5")
        assertTrue(vm.engineTurn)
        vm.resign()
        val game = Pgn.parse(vm.pgn("You", "Stockfish 1500"))
        assertEquals(fen, game.tags["FEN"])
        assertEquals(listOf("e5"), GameLine.from(game).plies.map { it.san })
        // With White as the user, the engine moves first here.
        vm.prepare(fen)
        vm.start(File("none"), EngineOptions(), white = true, elo = 1500)
        assertTrue(vm.engineTurn)
    }

    @Test
    fun lookBackAndForward() {
        val vm = PlayViewModel()
        vm.start(File("none"), EngineOptions(), white = true, elo = 1500)
        vm.play("e2e4")
        val live = vm.shownFen
        vm.back()
        assertEquals(0, vm.viewPly)
        assertEquals(GameLine.START_FEN, vm.shownFen)
        vm.forward()
        assertEquals(null, vm.viewPly)
        assertEquals(live, vm.shownFen)
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
