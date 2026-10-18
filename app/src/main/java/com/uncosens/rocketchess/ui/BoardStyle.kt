package com.uncosens.rocketchess.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Board looks. ROCKET uses the Night board colors in every theme. Plain ones are two colors; textured ones draw
 * assets/boards/<image>.jpg (from the Lichess project). [light]/[dark] also color the coordinates.
 */
enum class BoardStyle(val id: String, val light: Color, val dark: Color, val image: String? = null) {
    ROCKET("rocket", Color.Unspecified, Color.Unspecified),
    BROWN("brown", Color(0xFFF0D9B5), Color(0xFFB58863)),
    GREEN("green", Color(0xFFFFFFDD), Color(0xFF86A666)),
    BLUE("blue", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
    WOOD("wood", Color(0xFFDDB97E), Color(0xFF8E5B30), "wood4"),
    MAPLE("maple", Color(0xFFE9CC97), Color(0xFFBB8B52), "maple"),
    MARBLE("marble", Color(0xFFDCDAD2), Color(0xFF8E9A93), "marble"),
    BLUE_MARBLE("blue-marble", Color(0xFFD7E0E8), Color(0xFF6F8FAA), "blue-marble");

    companion object {
        val DEFAULT = ROCKET
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Keeps the chosen board style in SharedPreferences and loads its texture. */
object BoardStyleStore {
    private const val KEY = "board_style"

    private fun prefs(c: Context) = c.getSharedPreferences("board", Context.MODE_PRIVATE)

    fun load(c: Context) = BoardStyle.fromId(prefs(c).getString(KEY, null))

    fun save(c: Context, style: BoardStyle) = prefs(c).edit().putString(KEY, style.id).apply()

    fun texture(c: Context, style: BoardStyle): ImageBitmap? = style.image?.let { name ->
        c.assets.open("boards/$name.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }
}
