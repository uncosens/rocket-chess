package com.uncosens.rocketchess.engine

import android.content.Context
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Owns one Stockfish process and talks UCI over stdin/stdout. */
class StockfishSession(exe: File) : Closeable {
    private val process = ProcessBuilder(exe.path).start()
    private val writer = process.outputStream.bufferedWriter()
    private val lines = LinkedBlockingQueue<String>()

    init {
        thread(name = "sf-stdout", isDaemon = true) {
            runCatching { process.inputStream.bufferedReader().forEachLine(lines::put) }
            lines.put(EOF)
        }
        // stderr is for diagnostics only, never parsed.
        thread(name = "sf-stderr", isDaemon = true) {
            runCatching { process.errorStream.bufferedReader().forEachLine { Log.w(TAG, it) } }
        }
    }

    val isAlive: Boolean get() = process.isAlive

    fun send(command: String) {
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    /** Reads stdout until [done] matches a line. Returns every line read, including the match. */
    fun readUntil(timeoutMs: Long, done: (String) -> Boolean): List<String> {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val read = mutableListOf<String>()
        while (true) {
            val left = deadline - System.nanoTime()
            val line = lines.poll(left, TimeUnit.NANOSECONDS)
                ?: throw IOException("engine timeout after ${timeoutMs}ms")
            if (line === EOF) throw IOException("engine exited (code ${exitCodeOrNull()})")
            read += line
            if (done(line)) return read
        }
    }

    override fun close() {
        runCatching { send("quit") }
        if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private fun exitCodeOrNull() = runCatching { process.exitValue() }.getOrNull()

    companion object {
        private const val TAG = "Stockfish"
        private val EOF = String(charArrayOf('\u0000'))

        fun binary(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
    }
}
