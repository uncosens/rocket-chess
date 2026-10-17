package com.uncosens.rocketchess.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

/** A saved game. [pgn] is the raw text as imported. [sourceKey] stops the same game being saved twice. */
@Entity(tableName = "games", indices = [Index(value = ["sourceKey"], unique = true)])
data class SavedGame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val sourceKey: String,
    val white: String,
    val black: String,
    val result: String,
    val date: String,
    val pgn: String,
    val importedAt: Long,
)

/** List row without the PGN text. */
data class GameSummary(val id: Long, val source: String, val white: String, val black: String, val result: String, val date: String)

@Dao
interface GameDao {
    /** Returns -1 when a game with the same sourceKey exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: SavedGame): Long

    @Query("SELECT id FROM games WHERE sourceKey = :key")
    suspend fun idFor(key: String): Long?

    @Query("SELECT id, source, white, black, result, date FROM games ORDER BY importedAt DESC, id DESC")
    fun summaries(): Flow<List<GameSummary>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): SavedGame?

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}

@Database(entities = [SavedGame::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "games.db").build().also { instance = it }
        }
    }
}

object Sources {
    const val PASTE = "paste"
    const val FILE = "file"
    const val CHESS_COM = "chesscom"
    const val LICHESS = "lichess"

    /** Pasted and file games have no id, so the text itself is the key. Same text = same game. */
    fun textKey(pgn: String): String =
        "pgn:" + MessageDigest.getInstance("SHA-256").digest(pgn.trim().toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Saves a game or finds the copy saved before. Returns the row id. */
suspend fun GameDao.saveOrFind(game: SavedGame): Long {
    val id = insert(game)
    return if (id != -1L) id else idFor(game.sourceKey)!!
}
