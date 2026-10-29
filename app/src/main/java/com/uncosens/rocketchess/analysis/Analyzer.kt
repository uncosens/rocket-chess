package com.uncosens.rocketchess.analysis

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.engine.StockfishSession
import com.uncosens.rocketchess.engine.Uci

/** Engine result for one position. [best] is PV1's first move in UCI, null if the game is over. [secondBest] is PV2's. */
data class PositionEval(val eval: Eval, val best: String?, val second: Eval?, val depth: Int, val secondBest: String? = null)

data class MoveResult(val cls: MoveClass, val loss: Double)

/** Settings used for a run, kept with the results so a run can be repeated. */
data class AnalysisSettings(
    val engine: String,
    val depth: Int = AnalysisPolicy.DEPTH,
    val multiPv: Int = AnalysisPolicy.MULTI_PV,
    val threads: Int = AnalysisPolicy.THREADS,
    val hashMb: Int = AnalysisPolicy.HASH_MB,
)

/** Runs a fixed-depth search on each position. Blocking; call from a background thread. */
class Analyzer(private val sf: StockfishSession) {
    /** Sets options and returns the settings used, with the engine name. */
    fun start(opts: EngineOptions = EngineOptions()): AnalysisSettings {
        sf.send("uci")
        val name = sf.readUntil(TIMEOUT_MS) { it == "uciok" }
            .firstOrNull { it.startsWith("id name ") }?.removePrefix("id name ") ?: "unknown"
        val s = AnalysisSettings(name, depth = opts.depth, threads = opts.threads, hashMb = opts.hashMb)
        sf.send("setoption name Threads value ${s.threads}")
        sf.send("setoption name Hash value ${s.hashMb}")
        sf.send("setoption name MultiPV value ${s.multiPv}")
        sf.send("ucinewgame")
        sf.send("isready")
        sf.readUntil(TIMEOUT_MS) { it == "readyok" }
        return s
    }

    /**
     * Searches [fen] to [depth], or until [maxMs] passes; then the deepest finished result counts.
     * [onDepth], if given, gets a result each time a deeper depth is done.
     */
    fun analyze(fen: String, depth: Int, maxMs: Long = AnalysisPolicy.SEARCH_MS, onDepth: ((PositionEval) -> Unit)? = null): PositionEval {
        val whiteToMove = fen.split(' ').getOrNull(1) != "b"
        sf.send("position fen $fen")
        sf.send("go depth $depth movetime $maxMs")
        var shown = 0
        var second: Eval? = null
        var secondBest: String? = null
        val lines = sf.readUntil(SEARCH_TIMEOUT_MS) { line ->
            if (onDepth != null) Uci.parseInfo(line)?.let { info ->
                // MultiPV 2 comes after MultiPV 1 at each depth, so the second eval is one depth behind.
                if (info.multipv == 2) {
                    second = Eval.fromUci(info.score, whiteToMove)
                    secondBest = info.pv.firstOrNull()
                } else if (info.multipv == 1 && info.depth > shown) {
                    shown = info.depth
                    onDepth(PositionEval(Eval.fromUci(info.score, whiteToMove), info.pv.firstOrNull(), second, info.depth, secondBest))
                }
            }
            line.startsWith("bestmove")
        }
        // Keep the deepest line for each multipv slot.
        val infos = lines.mapNotNull(Uci::parseInfo).groupBy { it.multipv }.mapValues { (_, v) -> v.maxBy { it.depth } }
        val pv1 = infos[1] ?: throw IllegalStateException("no score for position")
        return PositionEval(
            eval = Eval.fromUci(pv1.score, whiteToMove),
            best = pv1.pv.firstOrNull() ?: Uci.parseBestMove(lines.last()),
            second = infos[2]?.let { Eval.fromUci(it.score, whiteToMove) },
            depth = pv1.depth,
            secondBest = infos[2]?.pv?.firstOrNull(),
        )
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val SEARCH_TIMEOUT_MS = 120_000L

        /** Grades ply i from the evals of positions i and i+1. Null until both are known. */
        fun grade(line: GameLine, evals: List<PositionEval?>, ply: Int): MoveResult? {
            val before = evals.getOrNull(ply) ?: return null
            val after = evals.getOrNull(ply + 1) ?: return null
            val whiteMoved = line.positions[ply].fen.split(' ')[1] == "w"
            val loss = winLoss(before.eval, after.eval, whiteMoved)
            val p = line.plies[ply]
            val white = whiteWinPercent(before.eval)
            val moverWin = if (whiteMoved) white else 100 - white
            val second = before.second?.let { whiteWinPercent(it) }?.let { if (whiteMoved) it else 100 - it }
            // Recapture: the last move took something and this move takes back on the same square.
            val prev = line.plies.getOrNull(ply - 1)
            val recapture = prev != null && 'x' in prev.san && 'x' in p.san && prev.to == p.to
            val moveNumber = line.positions[ply].fen.split(' ').getOrNull(5)?.toIntOrNull() ?: 1
            val cls = when {
                isBrilliant(p.uci, before.best, p.sacrifice, loss, moverWin) -> MoveClass.Brilliant
                isGreat(p.uci, before.best, moverWin, second, recapture, moveNumber) -> MoveClass.Great
                else -> classify(p.uci, before.best, loss)
            }
            return MoveResult(cls, loss)
        }

        /** One side's accuracy: the mean move score of its graded moves, 0 to 100; null if none are graded. */
        fun accuracy(line: GameLine, evals: List<PositionEval?>, white: Boolean): Double? =
            line.plies.indices.filter { (line.positions[it].fen.split(' ')[1] == "w") == white }
                .mapNotNull { i ->
                    val before = evals.getOrNull(i) ?: return@mapNotNull null
                    val after = evals.getOrNull(i + 1) ?: return@mapNotNull null
                    moveScore(winLoss(before.eval, after.eval, white, AnalysisPolicy.ACCURACY_SLOPE))
                }
                .takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 100.0)
    }
}
