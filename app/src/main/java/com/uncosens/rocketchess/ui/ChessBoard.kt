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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.uncosens.rocketchess.analysis.MoveClass

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

/** Draws one position from White's side. [lastMove] is (from, to); [grade] adds a badge on its target. */
@Composable
fun ChessBoard(
    fen: String,
    lastMove: Pair<String, String>?,
    check: String?,
    description: String,
    modifier: Modifier = Modifier,
    grade: MoveClass? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalBoardColors.current
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val sq = size.minDimension / 8
        val last = lastMove?.let { setOf(index(it.first), index(it.second)) } ?: emptySet()
        val style = TextStyle(fontSize = (sq * 0.8f).toSp())
        val coord = TextStyle(fontSize = (sq * 0.2f).toSp(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        for (i in 0 until 64) {
            val topLeft = Offset((i % 8) * sq, (7 - i / 8) * sq)
            val dark = (i % 8 + i / 8) % 2 == 0
            val fill = when {
                i in last -> if (dark) colors.lastDark else colors.lastLight
                dark -> colors.dark
                else -> colors.light
            }
            drawRect(fill, topLeft, Size(sq, sq))
            if (check != null && i == index(check)) drawRect(CHECK, topLeft, Size(sq, sq))
            // Rank numbers on the a-file, file letters on rank 1, in the other square color.
            val ink = if (dark) colors.light else colors.dark
            if (i % 8 == 0) drawText(measurer, "${i / 8 + 1}", topLeft + Offset(sq * 0.06f, sq * 0.02f), coord.copy(color = ink))
            if (i / 8 == 0) {
                val t = measurer.measure("${'a' + i % 8}", coord)
                drawText(t, ink, topLeft + Offset(sq - t.size.width - sq * 0.06f, sq - t.size.height))
            }
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
        val mark = grade?.badge()
        if (grade != null && mark != null && lastMove != null) {
            val to = index(lastMove.second)
            val center = Offset((to % 8) * sq + sq * 0.8f, (7 - to / 8) * sq + sq * 0.2f)
            drawCircle(grade.color(), sq * 0.19f, center)
            val t = measurer.measure(mark, TextStyle(fontSize = (sq * 0.22f).toSp(), fontWeight = FontWeight.Bold))
            drawText(t, Color.White, center - Offset(t.size.width / 2f, t.size.height / 2f))
        }
    }
}
