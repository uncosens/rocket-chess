package com.uncosens.rocketchess.analysis

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.engine.StockfishSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Full-game analysis with the real engine. Checks protocol and view, not exact scores.
@RunWith(AndroidJUnit4::class)
class AnalyzerDeviceTest {
    private val opera = """
        1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7
        8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7
        14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0
    """.trimIndent()

    @Test
    fun analyzesOperaGame() {
        val line = GameLine.from(Pgn.parse(opera))
        val exe = StockfishSession.binary(InstrumentationRegistry.getInstrumentation().targetContext)
        val started = System.nanoTime()
        val evals = StockfishSession(exe).use { sf ->
            val a = Analyzer(sf)
            val settings = a.start()
            assertTrue(settings.engine.startsWith("Stockfish"))
            line.positions.map { a.analyze(it.fen, settings.depth) }
        }
        Log.i("AnalyzerTest", "34 positions at depth ${AnalysisPolicy.DEPTH}: ${(System.nanoTime() - started) / 1_000_000} ms")

        assertEquals(line.positions.size, evals.size)
        // Start position: roughly equal, from White's view.
        val start = evals[0].eval as Eval.Cp
        assertTrue("start ${start.cp}", start.cp in -100..100)
        assertTrue(evals[0].second != null) // MultiPV 2
        // After 16... Nxb8 White mates in 1; after 17. Rd8# Black is mated.
        assertEquals(Eval.Mate(whiteWins = true, moves = 1), evals[32].eval)
        assertEquals(Eval.Mate(whiteWins = true, moves = 0), evals[33].eval)
        assertNull(evals[33].best)
        assertEquals(MoveClass.Best, Analyzer.grade(line, evals, 32)!!.cls)
        // Every move gets a grade.
        line.plies.indices.forEach { assertTrue(Analyzer.grade(line, evals, it) != null) }
    }
}
