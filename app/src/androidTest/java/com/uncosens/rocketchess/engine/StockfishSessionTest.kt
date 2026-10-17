package com.uncosens.rocketchess.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

// Runs the real packaged Stockfish binary on the device.
@RunWith(AndroidJUnit4::class)
class StockfishSessionTest {
    private val exe = StockfishSession.binary(InstrumentationRegistry.getInstrumentation().targetContext)
    private val bestmove = Regex("^bestmove [a-h][1-8][a-h][1-8][qrbn]?( ponder \\S+)?$")

    private fun StockfishSession.handshake() {
        send("uci")
        readUntil(5_000) { it == "uciok" }
        send("isready")
        readUntil(5_000) { it == "readyok" }
    }

    @Test
    fun uciRoundTrip(): Unit = StockfishSession(exe).use { sf ->
        sf.handshake()
        sf.send("ucinewgame")
        sf.send("isready")
        sf.readUntil(5_000) { it == "readyok" }
        sf.send("position startpos moves e2e4")
        sf.send("go depth 12")
        val out = sf.readUntil(60_000) { it.startsWith("bestmove") }
        assertTrue(out.last(), bestmove.matches(out.last()))
        assertTrue(out.any { " score cp " in it || " score mate " in it })
        // Embedded net must load without errors.
        assertFalse(out.any { "nnue" in it.lowercase() && "error" in it.lowercase() })
    }

    @Test
    fun stopEndsInfiniteSearch(): Unit = StockfishSession(exe).use { sf ->
        sf.handshake()
        sf.send("position startpos")
        sf.send("go infinite")
        Thread.sleep(500)
        sf.send("stop")
        val out = sf.readUntil(5_000) { it.startsWith("bestmove") }
        assertTrue(bestmove.matches(out.last()))
        // Session is usable after stop.
        sf.send("isready")
        sf.readUntil(5_000) { it == "readyok" }
    }

    @Test
    fun quitEndsProcess() {
        val sf = StockfishSession(exe)
        sf.handshake()
        sf.close()
        assertFalse(sf.isAlive)
    }

    @Test
    fun crashIsReportedAndNewSessionWorks() {
        val sf = StockfishSession(exe)
        sf.handshake()
        android.os.Process.sendSignal(pidOf(sf), 9)
        val err = runCatching { sf.readUntil(5_000) { false } }.exceptionOrNull()
        assertTrue(err is IOException)
        StockfishSession(exe).use { it.handshake() }
    }

    // ponytail: reflection on the private process; fine for a test.
    private fun pidOf(sf: StockfishSession): Int {
        val p = StockfishSession::class.java.getDeclaredField("process").apply { isAccessible = true }.get(sf)
        return p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p)
    }

    @Test
    fun binaryIsPackagedForArm64() {
        val header = ByteArray(20).also { java.io.RandomAccessFile(exe, "r").use { f -> f.readFully(it) } }
        assertEquals(0x7f, header[0].toInt())
        assertEquals("ELF", String(header, 1, 3))
        assertEquals(2, header[4].toInt()) // 64-bit
        assertEquals(183, header[18].toInt() and 0xff) // EM_AARCH64
        assertTrue(exe.canExecute())
    }
}
