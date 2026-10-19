package com.uncosens.rocketchess.analysis

/**
 * A run's settings and per-position results as compact text, saved with the game.
 * Line 1: engine, depth, MultiPV, threads, hash (tab separated). Line 2: one entry per position,
 * ";" separated, each "eval,best,second,depth,secondBest" (empty when not analyzed). Evals: "c35" or "m+3" / "m-3".
 */
object SavedAnalysis {
    fun encode(settings: AnalysisSettings, evals: List<PositionEval?>): String {
        val head = listOf(settings.engine.replace(Regex("[\t\n]"), " "), settings.depth, settings.multiPv, settings.threads, settings.hashMb)
        val body = evals.joinToString(";") { e ->
            if (e == null) "" else listOf(enc(e.eval), e.best.orEmpty(), e.second?.let(::enc).orEmpty(), e.depth, e.secondBest.orEmpty()).joinToString(",")
        }
        return head.joinToString("\t") + "\n" + body
    }

    /** Null if [text] cannot be read. */
    fun decode(text: String): Pair<AnalysisSettings, List<PositionEval?>>? = runCatching {
        val (head, body) = text.split("\n", limit = 2)
        val h = head.split("\t")
        val settings = AnalysisSettings(h[0], depth = h[1].toInt(), multiPv = h[2].toInt(), threads = h[3].toInt(), hashMb = h[4].toInt())
        val evals = body.split(";").map { entry ->
            if (entry.isEmpty()) null
            else entry.split(",").let { f -> PositionEval(dec(f[0]), f[1].ifEmpty { null }, f[2].ifEmpty { null }?.let(::dec), f[3].toInt(), f.getOrNull(4)?.ifEmpty { null }) }
        }
        settings to evals
    }.getOrNull()

    private fun enc(e: Eval) = when (e) {
        is Eval.Cp -> "c${e.cp}"
        is Eval.Mate -> "m${if (e.whiteWins) "+" else "-"}${e.moves}"
    }

    private fun dec(s: String): Eval = if (s[0] == 'c') Eval.Cp(s.substring(1).toInt()) else Eval.Mate(s[1] == '+', s.substring(2).toInt())
}
