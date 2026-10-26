package com.uncosens.rocketchess.analysis

/**
 * One game's part of My Report, kept so the report opens without replaying every game.
 * Made again when the game's analysis ([analyzedAt]) or its [owner] changes.
 * [usable] is false for a game the report leaves out; [sameGame] spots one game saved twice.
 */
data class ReportEntry(val analyzedAt: Long, val owner: String, val usable: Boolean, val sameGame: String, val moves: List<ReportMove>)

/** Text form of the per-game entries: a version line, then one tab-separated line per game. */
object ReportCache {
    /** Raise when [PlayerReport.moves] or [ReportPolicy] changes, so old entries are made again. */
    const val VERSION = 1

    fun encode(entries: Map<String, ReportEntry>): String = buildString {
        append("v$VERSION\n")
        for ((key, e) in entries) {
            val moves = e.moves.joinToString(";") { m ->
                val flags = (if (m.chance) 1 else 0) + (if (m.found) 2 else 0) + (if (m.kingError) 4 else 0)
                "${m.axis.ordinal},${m.score},${m.weight},$flags"
            }
            append(listOf(key, e.analyzedAt, e.owner, if (e.usable) 1 else 0, e.sameGame, moves).joinToString("\t")).append('\n')
        }
    }

    /** Entries by game key; empty when the text is from another version or unreadable. */
    fun decode(text: String): Map<String, ReportEntry> {
        val lines = text.lines()
        if (lines.firstOrNull() != "v$VERSION") return emptyMap()
        return lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { line ->
            runCatching {
                val f = line.split('\t')
                val moves = if (f[5].isEmpty()) emptyList() else f[5].split(';').map { m ->
                    val p = m.split(',')
                    val flags = p[3].toInt()
                    ReportMove(Axis.entries[p[0].toInt()], p[1].toDouble(), p[2].toDouble(), flags and 1 != 0, flags and 2 != 0, flags and 4 != 0)
                }
                f[0] to ReportEntry(f[1].toLong(), f[2], f[3] == "1", f[4], moves)
            }.getOrNull()
        }.toMap()
    }
}
