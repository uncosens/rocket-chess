package com.uncosens.rocketchess.remote

import kotlinx.serialization.Serializable

/** Lichess "Export games of a user" as NDJSON with the PGN inside each line. */
object Lichess {
    @Serializable
    data class Game(
        val id: String,
        val variant: String = "standard",
        val createdAt: Long = 0,
        val speed: String? = null,
        val players: Players,
        val pgn: String? = null,
    )

    @Serializable
    data class Players(val white: Player, val black: Player)

    @Serializable
    data class Player(val user: User? = null, val aiLevel: Int? = null, val rating: Int? = null)

    @Serializable
    data class User(val name: String)

    private fun Player.label() = user?.name ?: aiLevel?.let { "Stockfish level $it" } ?: "Anonymous"

    /** One game per line. A bad line is skipped so one broken game does not stop the import. */
    private fun decode(body: String): List<Game> = body.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        runCatching { Http.json.decodeFromString<Game>(line) }.getOrNull()
    }.toList()

    private fun List<Game>.playable() = filter { (it.variant == "standard" || it.variant == "fromPosition") && !it.pgn.isNullOrBlank() }.map {
        RemoteGame(
            "lichess:${it.id}", it.players.white.label(), it.players.black.label(), it.createdAt, it.speed, it.pgn!!,
            it.players.white.rating, it.players.black.rating,
        )
    }

    fun parseNdjson(body: String): List<RemoteGame> = decode(body).playable()

    /** Asks for games created before the oldest one seen so far. */
    class Pager(name: String) : GamePager {
        private val user = Http.checkName(name)
        private var until: Long? = null
        override var done = false
            private set

        override suspend fun next(): List<RemoteGame> {
            val url = "https://lichess.org/api/games/user/$user?max=${GamePager.PAGE}&pgnInJson=true&clocks=false&evals=false&opening=false" +
                (until?.let { "&until=$it" } ?: "")
            val games = decode(Http.get(url, accept = "application/x-ndjson"))
            // Skipped variants still move the cursor, so a page of them cannot repeat forever.
            games.minOfOrNull { it.createdAt }?.let { until = it - 1 }
            done = games.size < GamePager.PAGE
            return games.playable()
        }
    }
}
