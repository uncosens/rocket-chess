package com.uncosens.rocketchess.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Chess.com PubAPI: list monthly archives, then read the newest months. */
object ChessCom {
    private const val MAX_MONTHS = 3

    @Serializable
    data class Archives(val archives: List<String> = emptyList())

    @Serializable
    data class Month(val games: List<Game> = emptyList())

    @Serializable
    data class Game(
        val url: String,
        val uuid: String? = null,
        val pgn: String? = null,
        val rules: String = "chess",
        @SerialName("end_time") val endTime: Long = 0,
        @SerialName("time_class") val timeClass: String? = null,
        val white: Player,
        val black: Player,
    )

    @Serializable
    data class Player(val username: String = "?")

    fun parseArchives(json: String): List<String> =
        runCatching { Http.json.decodeFromString<Archives>(json).archives }.getOrElse { throw FetchError.BadResponse }

    /** Standard chess games with a PGN. Variants are skipped. */
    fun parseMonth(json: String): List<RemoteGame> {
        val month = runCatching { Http.json.decodeFromString<Month>(json) }.getOrElse { throw FetchError.BadResponse }
        return month.games.filter { it.rules == "chess" && !it.pgn.isNullOrBlank() }.map {
            RemoteGame("chesscom:${it.uuid ?: it.url}", it.white.username, it.black.username, it.endTime * 1000, it.timeClass, it.pgn!!)
        }
    }

    /** Newest games first, reading at most [MAX_MONTHS] months. */
    suspend fun recentGames(name: String, limit: Int = 30): List<RemoteGame> {
        val user = Http.checkName(name).lowercase()
        val months = parseArchives(Http.get("https://api.chess.com/pub/player/$user/games/archives")).reversed()
        val games = mutableListOf<RemoteGame>()
        // Archive URLs come from Chess.com; only follow ones on its API host.
        for (url in months.filter { it.startsWith("https://api.chess.com/") }.take(MAX_MONTHS)) {
            games += parseMonth(Http.get(url))
            if (games.size >= limit) break
        }
        return games.sortedByDescending { it.endedAt }.take(limit)
    }
}
