package com.uncosens.rocketchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

private val LIGHT = Color(0xFFEDE6D6)
private val DARK = Color(0xFF8CA2AD)
private val LAST_MOVE = Color(0x99F2C14E)
private val CHECK = Color(0xCCE5484D)
private val INK = Color(0xFF1E1E1E)

// Solid glyphs fill the piece shape; outline glyphs draw its lines. U+FE0E asks for text, not emoji.
private val SOLID = mapOf('k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟︎")
private val OUTLINE = mapOf('k' to "♔", 'q' to "♕", 'r' to "♖", 'b' to "♗", 'n' to "♘", 'p' to "♙︎")

/** Square name like "e4" to 0..63, a1 = 0. */
private fun index(square: String) = (square[0] - 'a') + (square[1] - '1') * 8

/** Piece letters by square index, from the FEN board field. */
private fun pieces(fen: String): Map<Int, Char> {
    val map = HashMap<Int, Char>()
    fen.substringBefore(' ').split('/').forEachIndexed { row, rank ->
        var file = 0
        for (c in rank) {
            if (c.isDigit()) file += c - '0' else map[(7 - row) * 8 + file++] = c
        }
    }
    return map
}

/** Draws one position from White's side. [lastMove] is (from, to). */
@Composable
fun ChessBoard(fen: String, lastMove: Pair<String, String>?, check: String?, description: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val sq = size.minDimension / 8
        val marked = buildMap {
            lastMove?.let { put(index(it.first), LAST_MOVE); put(index(it.second), LAST_MOVE) }
            check?.let { put(index(it), CHECK) }
        }
        val style = TextStyle(fontSize = (sq * 0.8f).toSp())
        for (i in 0 until 64) {
            val topLeft = Offset((i % 8) * sq, (7 - i / 8) * sq)
            drawRect(if ((i % 8 + i / 8) % 2 == 0) DARK else LIGHT, topLeft, Size(sq, sq))
            marked[i]?.let { drawRect(it, topLeft, Size(sq, sq)) }
        }
        // ponytail: system font glyphs; draw our own vector pieces if a device font looks wrong.
        for ((i, c) in pieces(fen)) {
            val solid = measurer.measure(SOLID[c.lowercaseChar()] ?: continue, style)
            val at = Offset(
                (i % 8) * sq + (sq - solid.size.width) / 2,
                (7 - i / 8) * sq + (sq - solid.size.height) / 2,
            )
            if (c.isUpperCase()) {
                drawText(solid, color = Color.White, topLeft = at)
                drawText(measurer.measure(OUTLINE.getValue(c.lowercaseChar()), style), color = INK, topLeft = at)
            } else {
                drawText(solid, color = INK, topLeft = at)
            }
        }
    }
}
