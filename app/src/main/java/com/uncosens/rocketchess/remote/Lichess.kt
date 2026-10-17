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
    data class Player(val user: User? = null, val aiLevel: Int? = null)

    @Serializable
    data class User(val name: String)

    private fun Player.label() = user?.name ?: aiLevel?.let { "Stockfish level $it" } ?: "Anonymous"

    /** One game per line. A bad line is skipped so one broken game does not stop the import. */
    fun parseNdjson(body: String): List<RemoteGame> = body.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        runCatching { Http.json.decodeFromString<Game>(line) }.getOrNull()
    }.filter { (it.variant == "standard" || it.variant == "fromPosition") && !it.pgn.isNullOrBlank() }.map {
        RemoteGame("lichess:${it.id}", it.players.white.label(), it.players.black.label(), it.createdAt, it.speed, it.pgn!!)
    }.toList()

    suspend fun recentGames(name: String, limit: Int = 30): List<RemoteGame> {
        val user = Http.checkName(name)
        val url = "https://lichess.org/api/games/user/$user?max=$limit&pgnInJson=true&clocks=false&evals=false&opening=false"
        return parseNdjson(Http.get(url, accept = "application/x-ndjson"))
    }
}
