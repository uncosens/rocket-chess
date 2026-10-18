package com.uncosens.rocketchess.remote

import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.userSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// Fixtures follow the documented and observed response shapes. Passing here does not prove the live services work.
class RemoteTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun chessComArchivesAndMonth() {
        val months = ChessCom.parseArchives(fixture("chesscom-archives.json"))
        assertEquals(3, months.size)
        assertEquals("https://api.chess.com/pub/player/testuser/games/2026/10", months.last())

        val games = ChessCom.parseMonth(fixture("chesscom-month.json"))
        // chess960 and the game without PGN are skipped; unknown fields are ignored.
        assertEquals(listOf("chesscom:aaaa-1111", "chesscom:bbbb-2222"), games.map { it.key })
        assertEquals("rival", games[0].black)
        assertEquals(1790000000000, games[0].endedAt)
        assertEquals("blitz", games[0].speed)
        // The PGN parses and replays.
        games.forEach { GameLine.from(Pgn.parse(it.pgn)) }
    }

    @Test
    fun lichessNdjsonSkipsBadLinesAndVariants() {
        val games = Lichess.parseNdjson(fixture("lichess-games.ndjson"))
        assertEquals(listOf("lichess:abcd1234", "lichess:ai000001"), games.map { it.key })
        assertEquals("TestUser", games[0].white)
        assertEquals("Stockfish level 3", games[1].white)
        assertEquals("1-0", tag(games[0].pgn, "Result"))
        games.forEach { GameLine.from(Pgn.parse(it.pgn)) }
    }

    @Test
    fun badJsonIsBadResponse() {
        assertThrows(FetchError.BadResponse::class.java) { ChessCom.parseArchives("<html>oops</html>") }
        assertThrows(FetchError.BadResponse::class.java) { ChessCom.parseMonth("{\"games\": 5}") }
        assertEquals(emptyList<RemoteGame>(), Lichess.parseNdjson(""))
    }

    @Test
    fun httpErrors() {
        assertEquals(FetchError.NotFound, Http.errorFor(404, null))
        assertEquals(FetchError.NotFound, Http.errorFor(410, null))
        assertEquals(FetchError.RateLimited(120), Http.errorFor(429, "120"))
        assertEquals(FetchError.RateLimited(60), Http.errorFor(429, null))
        assertEquals(FetchError.RateLimited(60), Http.errorFor(429, "Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(FetchError.Server(503), Http.errorFor(503, null))
    }

    @Test
    fun userNames() {
        assertEquals("Magnus_C-1", Http.checkName("  Magnus_C-1 "))
        listOf("a", "bad name", "../x", "x?y=1", "é", "a".repeat(31)).forEach {
            assertThrows(it, FetchError.BadName::class.java) { Http.checkName(it) }
        }
    }

    @Test
    fun textKeysIgnoreOuterSpaceOnly() {
        assertEquals(Sources.textKey("1. e4 *"), Sources.textKey("\n1. e4 *  \n"))
        assertNotEquals(Sources.textKey("1. e4 *"), Sources.textKey("1. d4 *"))
    }

    @Test
    fun ratingsAndUserSide() {
        val chessCom = ChessCom.parseMonth(fixture("chesscom-month.json"))
        assertEquals(1500 to 1490, chessCom[0].whiteElo to chessCom[0].blackElo)
        assertEquals(null to null, chessCom[1].whiteElo to chessCom[1].blackElo)
        val lichess = Lichess.parseNdjson(fixture("lichess-games.ndjson"))
        assertEquals(1600 to 1580, lichess[0].whiteElo to lichess[0].blackElo)
        assertEquals(null, lichess[1].whiteElo) // Stockfish has no rating

        val names = setOf("testuser")
        assertEquals(true, userSide("TestUser", "Rival", names))
        assertEquals(false, userSide("Stockfish level 3", "testuser", names))
        assertEquals(null, userSide("a", "b", names))
    }
}
