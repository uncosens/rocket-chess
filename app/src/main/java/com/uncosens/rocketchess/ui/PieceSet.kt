package com.uncosens.rocketchess.ui

import android.content.Context
import com.caverock.androidsvg.SVG

/** Piece image sets in assets/pieces/<id>/. Rocket, Classic and Orbit are our own; the rest are from the Lichess project. */
enum class PieceSet(val id: String) {
    CBURNETT("cburnett"), ROCKET("rocket"), CLASSIC("classic"), ORBIT("orbit"),
    MERIDA("merida"), CHESSNUT("chessnut"), MPCHESS("mpchess");

    companion object {
        val DEFAULT = CBURNETT
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Keeps the chosen piece set in SharedPreferences and loads its images. */
object PieceSetStore {
    private const val KEY = "piece_set"

    private fun prefs(c: Context) = c.getSharedPreferences("board", Context.MODE_PRIVATE)

    fun load(c: Context) = PieceSet.fromId(prefs(c).getString(KEY, null))

    fun save(c: Context, set: PieceSet) = prefs(c).edit().putString(KEY, set.id).apply()

    /** FEN piece letter (K, q, ...) to its image. */
    fun images(c: Context, set: PieceSet): Map<Char, SVG> = "KQRBNPkqrbnp".associateWith { p ->
        val name = (if (p.isUpperCase()) "w" else "b") + p.uppercaseChar()
        c.assets.open("pieces/${set.id}/$name.svg").use { SVG.getFromInputStream(it) }.apply {
            // Some files set a fixed width/height; drop it so the image fills each square.
            setDocumentWidth("100%")
            setDocumentHeight("100%")
        }
    }
}
