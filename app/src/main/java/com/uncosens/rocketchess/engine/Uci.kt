package com.uncosens.rocketchess.engine

/** Engine score from the side to move's view, as UCI reports it. */
sealed interface UciScore {
    data class Cp(val cp: Int) : UciScore
    /** Mate in [moves]. Positive: side to move mates. Negative or 0: side to move is mated. */
    data class Mate(val moves: Int) : UciScore
}

data class UciInfo(val depth: Int, val multipv: Int, val score: UciScore, val pv: List<String>)

object Uci {
    /** Parses an "info" line that has a score. Returns null for other lines (currmove, string, bound scores). */
    fun parseInfo(line: String): UciInfo? {
        val t = line.split(' ').filter { it.isNotEmpty() }
        if (t.firstOrNull() != "info") return null
        var depth = -1
        var multipv = 1
        var score: UciScore? = null
        var pv = emptyList<String>()
        var i = 1
        while (i < t.size) {
            when (t[i]) {
                "depth" -> depth = t.getOrNull(++i)?.toIntOrNull() ?: return null
                "multipv" -> multipv = t.getOrNull(++i)?.toIntOrNull() ?: return null
                "score" -> {
                    val kind = t.getOrNull(++i)
                    val value = t.getOrNull(++i)?.toIntOrNull() ?: return null
                    score = when (kind) {
                        "cp" -> UciScore.Cp(value)
                        "mate" -> UciScore.Mate(value)
                        else -> return null
                    }
                    // Bound scores come from an unfinished search line; skip them.
                    if (t.getOrNull(i + 1) == "lowerbound" || t.getOrNull(i + 1) == "upperbound") return null
                }
                "pv" -> { pv = t.subList(i + 1, t.size); break }
            }
            i++
        }
        return if (depth >= 0 && score != null) UciInfo(depth, multipv, score, pv) else null
    }

    /** "bestmove e2e4 ponder e7e5" -> "e2e4". "(none)" -> null. */
    fun parseBestMove(line: String): String? =
        line.split(' ').getOrNull(1)?.takeIf { it != "(none)" }
}
