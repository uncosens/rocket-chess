package com.uncosens.rocketchess.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Chess.com PubAPI: list monthly archives, then read the newest months. */
object ChessCom {
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
    data class Player(val username: String = "?", val rating: Int? = null)

    fun parseArchives(json: String): List<String> =
        runCatching { Http.json.decodeFromString<Archives>(json).archives }.getOrElse { throw FetchError.BadResponse }

    /** Standard chess games with a PGN. Variants are skipped. */
    fun parseMonth(json: String): List<RemoteGame> {
        val month = runCatching { Http.json.decodeFromString<Month>(json) }.getOrElse { throw FetchError.BadResponse }
        return month.games.filter { it.rules == "chess" && !it.pgn.isNullOrBlank() }.map {
            RemoteGame(
                "chesscom:${it.uuid ?: it.url}", it.white.username, it.black.username, it.endTime * 1000, it.timeClass, it.pgn!!,
                it.white.rating, it.black.rating,
            )
        }
    }

    /** Reads months newest first, only as many as the next page needs. */
    class Pager(name: String) : GamePager {
        private val user = Http.checkName(name).lowercase()
        private var months: ArrayDeque<String>? = null
        private val buffer = mutableListOf<RemoteGame>()
        override var done = false
            private set

        override suspend fun next(): List<RemoteGame> {
            // Archive URLs come from Chess.com; only follow ones on its API host.
            val left = months ?: ArrayDeque(
                parseArchives(Http.get("https://api.chess.com/pub/player/$user/games/archives"))
                    .filter { it.startsWith("https://api.chess.com/") }.reversed(),
            ).also { months = it }
            while (buffer.size < GamePager.PAGE && left.isNotEmpty()) {
                buffer += parseMonth(Http.get(left.first())).sortedByDescending { it.endedAt }
                left.removeFirst()
            }
            val page = buffer.take(GamePager.PAGE)
            buffer.subList(0, page.size).clear()
            done = buffer.isEmpty() && left.isEmpty()
            return page
        }
    }
}
