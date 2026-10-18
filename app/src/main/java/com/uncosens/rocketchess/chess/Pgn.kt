package com.uncosens.rocketchess.chess

/** A SAN move as written in the PGN, with its 1-based position in the text. */
data class SanToken(val text: String, val line: Int, val column: Int)

/** How a decisive game ended. */
enum class Finish { MATE, RESIGNED, TIME }

data class PgnGame(val tags: Map<String, String>, val moves: List<SanToken>, val result: String?) {
    /**
     * Resignation or time loss in a decisive game, from the Termination tag; null if unknown.
     * Chess.com writes "... won by resignation" / "... won on time"; Lichess writes "Time forfeit",
     * and "Normal" for both resignation and mate, so a mated last position must be ruled out first.
     */
    fun finish(): Finish? {
        val r = result ?: tags["Result"]
        if (r != "1-0" && r != "0-1") return null
        val t = tags["Termination"] ?: return null
        return when {
            t.contains("on time", ignoreCase = true) || t.equals("Time forfeit", ignoreCase = true) -> Finish.TIME
            t.contains("resignation", ignoreCase = true) || t == "Normal" -> Finish.RESIGNED
            else -> null
        }
    }
}

class PgnException(val reason: String, val line: Int, val column: Int) :
    Exception("line $line, column $column: $reason")

/** Reads the first game of a PGN text. Comments, NAGs and variations are skipped. */
object Pgn {
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
    private val MOVE_NUMBER = Regex("^\\d+\\.+")

    fun parse(text: String): PgnGame {
        val tags = linkedMapOf<String, String>()
        val moves = mutableListOf<SanToken>()
        var i = 0
        var line = 1
        var col = 1
        var depth = 0 // variation depth; moves inside variations are ignored
        var depthLine = 0
        var depthCol = 0

        fun fail(reason: String, l: Int = line, c: Int = col): Nothing = throw PgnException(reason, l, c)
        fun advance() {
            if (text[i] == '\n') { line++; col = 1 } else col++
            i++
        }
        fun skipTo(end: Char, what: String) {
            val l = line; val c = col
            while (i < text.length && text[i] != end) advance()
            if (i >= text.length) fail("$what is not closed", l, c)
            advance()
        }
        fun done(result: String?): PgnGame {
            if (depth > 0) fail("variation is not closed", depthLine, depthCol)
            if (tags.isEmpty() && moves.isEmpty()) fail("no game found", 1, 1)
            return PgnGame(tags, moves, result)
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isWhitespace() || ch == '﻿' -> advance()
                ch == '%' && col == 1 -> skipTo('\n', "line")
                ch == ';' -> { while (i < text.length && text[i] != '\n') advance() }
                ch == '{' -> skipTo('}', "comment")
                ch == '!' || ch == '?' -> advance() // detached annotation like "e4 !?"
                ch == '$' -> { advance(); while (i < text.length && text[i].isDigit()) advance() }
                ch == '(' -> { if (depth++ == 0) { depthLine = line; depthCol = col }; advance() }
                ch == ')' -> { if (depth == 0) fail("unexpected ')'"); depth--; advance() }
                ch == '[' -> {
                    // A tag after moves starts the next game.
                    if (moves.isNotEmpty() && depth == 0) return done(null)
                    val l = line; val c = col
                    val end = text.indexOf(']', i)
                    if (end < 0) fail("tag is not closed")
                    val m = TAG.matchEntire(text.substring(i, end + 1)) ?: fail("bad tag", l, c)
                    tags[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
                    while (i <= end) advance()
                }
                ch.isLetterOrDigit() || ch == '*' -> {
                    val l = line; val c = col
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] in SYMBOL_CHARS)) advance()
                    val word = text.substring(start, i)
                    if (word in RESULTS) {
                        if (depth == 0) return done(word)
                        continue
                    }
                    val number = MOVE_NUMBER.find(word)?.value.orEmpty()
                    val san = word.substring(number.length)
                    if (san.isEmpty() || san.all { it.isDigit() }) continue // "12." or "12"
                    if (depth == 0) moves += SanToken(san, l, c + number.length)
                }
                else -> fail("unexpected '$ch'")
            }
        }
        return done(null)
    }

    private const val SYMBOL_CHARS = "-=+#!?/.:*"
    private val TAG = Regex("^\\[\\s*([A-Za-z0-9_]+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*]$", RegexOption.DOT_MATCHES_ALL)
}
