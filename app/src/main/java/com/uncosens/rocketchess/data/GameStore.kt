package com.uncosens.rocketchess.data

import android.content.Context
import androidx.room.AutoMigration
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
    /** Mean move accuracy per side, set once a full analysis finishes. */
    val whiteAccuracy: Double? = null,
    val blackAccuracy: Double? = null,
    /** The user name searched when the game was imported; their side is the user's side. */
    val owner: String? = null,
    /** The last analysis as SavedAnalysis text; kept for the latest analyzed games only. */
    val analysis: String? = null,
    val analyzedAt: Long? = null,
)

/** List row without the PGN text. */
data class GameSummary(
    val id: Long,
    val source: String,
    val white: String,
    val black: String,
    val result: String,
    val date: String,
    val whiteAccuracy: Double?,
    val blackAccuracy: Double?,
    val owner: String?,
)

/** What My Report and Mistake Notes need from a game with a saved analysis. */
data class AnalyzedGame(val source: String, val white: String, val black: String, val date: String, val pgn: String, val owner: String?, val analysis: String)

data class KeyAccuracy(val sourceKey: String, val whiteAccuracy: Double?, val blackAccuracy: Double?)

@Dao
interface GameDao {
    /** Returns -1 when a game with the same sourceKey exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: SavedGame): Long

    @Query("SELECT id FROM games WHERE sourceKey = :key")
    suspend fun idFor(key: String): Long?

    @Query("SELECT id, source, white, black, result, date, whiteAccuracy, blackAccuracy, owner FROM games ORDER BY importedAt DESC, id DESC")
    fun summaries(): Flow<List<GameSummary>>

    @Query("SELECT sourceKey, whiteAccuracy, blackAccuracy FROM games WHERE whiteAccuracy IS NOT NULL OR blackAccuracy IS NOT NULL")
    fun accuracies(): Flow<List<KeyAccuracy>>

    @Query("UPDATE games SET whiteAccuracy = :white, blackAccuracy = :black WHERE sourceKey = :key")
    suspend fun setAccuracy(key: String, white: Double?, black: Double?)

    @Query("SELECT source, white, black, date, pgn, owner, analysis FROM games WHERE analysis IS NOT NULL ORDER BY analyzedAt DESC")
    suspend fun analyzed(): List<AnalyzedGame>

    @Query("SELECT analysis FROM games WHERE sourceKey = :key")
    suspend fun analysisFor(key: String): String?

    @Query("UPDATE games SET analysis = :text, analyzedAt = :at WHERE sourceKey = :key")
    suspend fun setAnalysis(key: String, text: String, at: Long)

    /** Keeps the saved analysis of only the [keep] most recently analyzed games. */
    @Query(
        "UPDATE games SET analysis = NULL, analyzedAt = NULL WHERE analysis IS NOT NULL AND id NOT IN " +
            "(SELECT id FROM games WHERE analysis IS NOT NULL ORDER BY analyzedAt DESC LIMIT :keep)",
    )
    suspend fun pruneAnalyses(keep: Int)

    /** Makes [name] the owner of every saved game it played that has no owner yet. */
    @Query("UPDATE games SET owner = :name WHERE owner IS NULL AND (lower(white) = lower(:name) OR lower(black) = lower(:name))")
    suspend fun claimOwner(name: String)

    /** Sets the owner of a game saved before it had one. */
    @Query("UPDATE games SET owner = :owner WHERE sourceKey = :key AND owner IS NULL")
    suspend fun setOwnerIfMissing(key: String, owner: String)

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): SavedGame?

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}

@Database(entities = [SavedGame::class], version = 4, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)])
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
    const val SETUP = "setup"
    const val ENGINE = "engine"

    /** Pasted and file games have no id, so the text itself is the key. Same text = same game. */
    fun textKey(pgn: String): String =
        "pgn:" + MessageDigest.getInstance("SHA-256").digest(pgn.trim().toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Saves a game or finds the copy saved before. Returns the row id. */
suspend fun GameDao.saveOrFind(game: SavedGame): Long {
    val id = insert(game)
    return if (id != -1L) id else idFor(game.sourceKey)!!
}
