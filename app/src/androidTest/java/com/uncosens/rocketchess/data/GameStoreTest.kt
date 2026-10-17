package com.uncosens.rocketchess.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java,
    ).build()
    private val dao = db.games()

    private fun game(key: String, pgn: String = "1. e4 *") = SavedGame(
        source = Sources.LICHESS, sourceKey = key, white = "W", black = "B", result = "*", date = "", pgn = pgn, importedAt = 1,
    )

    @Test
    fun saveFindDelete() = runBlocking {
        val a = dao.saveOrFind(game("lichess:a"))
        val b = dao.saveOrFind(game("lichess:b"))
        // Same key again: no new row, the first copy is kept.
        assertEquals(a, dao.saveOrFind(game("lichess:a", pgn = "1. d4 *")))
        assertEquals("1. e4 *", dao.get(a)!!.pgn)
        assertEquals(2, dao.summaries().first().size)

        dao.delete(a)
        assertNull(dao.get(a))
        assertEquals(listOf(b), dao.summaries().first().map { it.id })

        dao.deleteAll()
        assertEquals(0, dao.summaries().first().size)
        db.close()
    }
}
