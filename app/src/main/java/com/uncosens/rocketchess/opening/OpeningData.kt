package com.uncosens.rocketchess.opening

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The book and names from the app's assets, loaded once in the background. Null until loaded. */
object OpeningData {
    var book by mutableStateOf<OpeningBook?>(null)
        private set
    var names by mutableStateOf<OpeningNames?>(null)
        private set
    /** The rating band shown in the explorer; null is all bands. Kept across runs. */
    var band by mutableStateOf<Int?>(null)
        private set

    /** Reads the files on a background thread; the state is set on the caller's (main) thread. */
    suspend fun load(c: Context) {
        if (book != null) return
        val (n, b) = withContext(Dispatchers.IO) {
            OpeningNames.parse(c.assets.open("opening/names.tsv").bufferedReader().use { it.readText() }) to
                OpeningBook(c.assets.open("opening/book.bin").use { it.readBytes() })
        }
        band = prefs(c).getInt(KEY_BAND, -1).takeIf { it >= 0 }
        names = n
        book = b
    }

    fun pickBand(c: Context, value: Int?) {
        band = value
        prefs(c).edit().putInt(KEY_BAND, value ?: -1).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("explorer", Context.MODE_PRIVATE)
    private const val KEY_BAND = "band"
}
