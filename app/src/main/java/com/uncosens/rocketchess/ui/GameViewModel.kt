package com.uncosens.rocketchess.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Pgn
import com.uncosens.rocketchess.chess.PgnException
import com.uncosens.rocketchess.chess.PgnGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LoadError {
    data class Parse(val e: PgnException) : LoadError
    data object TooLarge : LoadError
    data object Unreadable : LoadError
}

class GameViewModel : ViewModel() {
    /** Raw PGN text as imported. Kept so it can be re-parsed or saved later. */
    var raw by mutableStateOf<String?>(null)
        private set
    var game by mutableStateOf<PgnGame?>(null)
        private set
    var line by mutableStateOf<GameLine?>(null)
        private set
    /** Index into line.positions: 0 is the start, N is after N plies. */
    var ply by mutableStateOf(0)
        private set
    var error by mutableStateOf<LoadError?>(null)
        private set

    fun load(text: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { Pgn.parse(text).let { it to GameLine.from(it) } }
            }
            result.onSuccess { (g, l) ->
                raw = text; game = g; line = l; ply = 0; error = null
            }.onFailure {
                error = if (it is PgnException) LoadError.Parse(it) else LoadError.Unreadable
            }
        }
    }

    fun loadFile(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    // Read at most one byte past the limit to detect big files.
                    resolver.openInputStream(uri)!!.use { it.readNBytesCompat(MAX_BYTES + 1) }
                }.getOrNull()
            }
            when {
                bytes == null -> error = LoadError.Unreadable
                bytes.size > MAX_BYTES -> error = LoadError.TooLarge
                else -> load(bytes.toString(Charsets.UTF_8))
            }
        }
    }

    fun goTo(index: Int) {
        val last = line?.plies?.size ?: return
        ply = index.coerceIn(0, last)
    }

    companion object {
        // ponytail: whole file in memory, capped. Stream games if big databases are ever needed.
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
