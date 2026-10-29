package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.engine.StockfishSession
import java.io.Closeable
import java.io.File

/** Checks mistake positions with a deeper search and builds their puzzle lines. Blocking; use a background thread. */
class PuzzleChecker(exe: File, opts: EngineOptions) : Closeable {
    private val sf = StockfishSession(exe)
    private val analyzer = Analyzer(sf).also { it.start(opts) }

    /** The puzzle line from [fen], or null when the position makes no good puzzle. See [MistakePuzzles.line]. */
    fun line(fen: String, comeback: Boolean): List<String>? = MistakePuzzles.line(fen, comeback) { analyzer.analyze(it, PuzzlePolicy.DEPTH, PuzzlePolicy.SEARCH_MS) }

    override fun close() = sf.close()
}
