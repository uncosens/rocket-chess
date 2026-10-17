package com.uncosens.rocketchess.ui

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.uncosens.rocketchess.analysis.MoveClass
import kotlin.math.hypot
import kotlin.math.roundToInt

private val CHECK = Color(0xCCE5484D)
private val LAST = Color(0x66F6C548)
private val ARROW = Color(0xD91C93A8)
private val SELECTED = Color(0x801C93A8)
private val TARGET = Color(0x59101828)
private const val MOVE_MS = 220

/** Square name like "e4" to 0..63, a1 = 0. */
private fun index(square: String) = (square[0] - 'a') + (square[1] - '1') * 8

/** Piece letters by square index, from the FEN board field. */
internal fun pieces(fen: String): Map<Int, Char> {
    val map = HashMap<Int, Char>()
    fen.substringBefore(' ').split('/').forEachIndexed { row, rank ->
        var file = 0
        for (c in rank) {
            if (c.isDigit()) file += c - '0' else map[(7 - row) * 8 + file++] = c
        }
    }
    return map
}

/**
 * Pieces that moved from [old] to [new], as target square to source square.
 * A piece with no free source of its kind (promotion, piece coming back) just appears.
 */
internal fun slides(old: Map<Int, Char>, new: Map<Int, Char>): Map<Int, Int> {
    val sources = old.filter { (sq, p) -> new[sq] != p }.toMutableMap()
    val out = HashMap<Int, Int>()
    for ((sq, p) in new) {
        if (old[sq] == p) continue
        val from = sources.filterValues { it == p }.keys
            .minByOrNull { hypot((it % 8 - sq % 8).toDouble(), (it / 8 - sq / 8).toDouble()) } ?: continue
        sources.remove(from)
        out[sq] = from
    }
    return out
}

/**
 * Draws one position. [lastMove] and [arrow] are (from, to); [grade] adds a badge on the last move's target.
 * [flipped] shows the board from Black's side. Pieces slide when [fen] changes.
 * [pieceSet] and [boardStyle] default to the saved choices.
 * With [onTap], squares can be tapped; [selected] is marked and [targets] get a dot.
 */
@Composable
fun ChessBoard(
    fen: String,
    lastMove: Pair<String, String>?,
    check: String?,
    description: String,
    modifier: Modifier = Modifier,
    grade: MoveClass? = null,
    pieceSet: PieceSet? = null,
    flipped: Boolean = false,
    arrow: Pair<String, String>? = null,
    boardStyle: BoardStyle? = null,
    selected: String? = null,
    targets: Set<String> = emptySet(),
    onTap: ((String) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = LocalBoardColors.current
    val context = LocalContext.current
    // No set given: use the saved choice.
    val set = pieceSet ?: remember { PieceSetStore.load(context) }
    val images = remember(set) { PieceSetStore.images(context, set) }
    val style = boardStyle ?: remember { BoardStyleStore.load(context) }
    val texture = remember(style) { BoardStyleStore.texture(context, style) }

    val now = remember(fen) { pieces(fen) }
    var shown by remember { mutableStateOf(now) }
    val moves = remember(fen) { slides(shown, now) }
    val progress = remember(fen) { Animatable(if (moves.isEmpty()) 1f else 0f) }
    LaunchedEffect(fen) {
        shown = now
        progress.animateTo(1f, tween(MOVE_MS, easing = FastOutSlowInEasing))
    }

    val tap by rememberUpdatedState(onTap)
    val input = if (onTap == null) Modifier else Modifier.pointerInput(flipped) {
        detectTapGestures { o ->
            val col = (o.x / size.width * 8).toInt().coerceIn(0, 7)
            val row = (o.y / size.height * 8).toInt().coerceIn(0, 7)
            val file = if (flipped) 7 - col else col
            val rank = if (flipped) row else 7 - row
            tap?.invoke("${'a' + file}${rank + 1}")
        }
    }

    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }.then(input)) {
        val sq = size.minDimension / 8
        // Top-left corner of a square on screen.
        fun at(i: Int) = if (flipped) Offset((7 - i % 8) * sq, (i / 8) * sq) else Offset((i % 8) * sq, (7 - i / 8) * sq)
        fun center(i: Int) = at(i) + Offset(sq / 2, sq / 2)

        val last = lastMove?.let { setOf(index(it.first), index(it.second)) } ?: emptySet()
        val coord = TextStyle(fontSize = (sq * 0.2f).toSp(), fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        val themed = style == BoardStyle.ROCKET
        val lightSq = if (themed) colors.light else style.light
        val darkSq = if (themed) colors.dark else style.dark
        texture?.let { drawImage(it, dstSize = IntSize((sq * 8).roundToInt(), (sq * 8).roundToInt())) }
        for (i in 0 until 64) {
            val topLeft = at(i)
            val dark = (i % 8 + i / 8) % 2 == 0
            when {
                themed -> drawRect(if (i in last) (if (dark) colors.lastDark else colors.lastLight) else if (dark) darkSq else lightSq, topLeft, Size(sq, sq))
                texture == null -> drawRect(if (dark) darkSq else lightSq, topLeft, Size(sq, sq))
            }
            // Other styles mark the last move with a see-through overlay.
            if (!themed && i in last) drawRect(LAST, topLeft, Size(sq, sq))
            if (check != null && i == index(check)) drawRect(CHECK, topLeft, Size(sq, sq))
            if (selected != null && i == index(selected)) drawRect(SELECTED, topLeft, Size(sq, sq))
            // Rank numbers on the left edge, file letters on the bottom edge, in the other square color.
            val ink = if (dark) lightSq else darkSq
            if (i % 8 == (if (flipped) 7 else 0)) {
                drawText(measurer, "${i / 8 + 1}", topLeft + Offset(sq * 0.06f, sq * 0.02f), coord.copy(color = ink))
            }
            if (i / 8 == (if (flipped) 7 else 0)) {
                val t = measurer.measure("${'a' + i % 8}", coord)
                drawText(t, ink, topLeft + Offset(sq - t.size.width - sq * 0.06f, sq - t.size.height))
            }
        }

        val p = progress.value
        drawIntoCanvas { canvas ->
            // Still pieces first, so sliding ones pass over them.
            for ((i, c) in now.entries.sortedBy { it.key in moves }) {
                val o = moves[i]?.let { lerp(at(it), at(i), p) } ?: at(i)
                images[c]?.renderToCanvas(canvas.nativeCanvas, RectF(o.x, o.y, o.x + sq, o.y + sq))
            }
        }
        if (p < 1f) return@Canvas

        // A dot on an empty target, a ring on a capture.
        for (t in targets) {
            val i = index(t)
            if (i in now) drawCircle(TARGET, sq * 0.46f, center(i), style = Stroke(sq * 0.08f))
            else drawCircle(TARGET, sq * 0.16f, center(i))
        }
        if (arrow != null) drawArrow(center(index(arrow.first)), center(index(arrow.second)), sq)
        val mark = grade?.badge()
        if (grade != null && mark != null && lastMove != null) {
            val c = at(index(lastMove.second)) + Offset(sq * 0.8f, sq * 0.2f)
            drawCircle(grade.color(), sq * 0.19f, c)
            val t = measurer.measure(mark, TextStyle(fontSize = (sq * 0.22f).toSp(), fontWeight = FontWeight.Bold))
            drawText(t, Color.White, c - Offset(t.size.width / 2f, t.size.height / 2f))
        }
    }
}

/** A straight arrow between square centers, stopping short of the target center. */
private fun DrawScope.drawArrow(from: Offset, to: Offset, sq: Float) {
    val d = to - from
    val len = d.getDistance()
    if (len == 0f) return
    val dir = d / len
    val side = Offset(-dir.y, dir.x)
    val head = sq * 0.42f
    val tip = to - dir * (sq * 0.12f)
    val base = tip - dir * head
    drawLine(ARROW, from, base, strokeWidth = sq * 0.17f, cap = StrokeCap.Round)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + side.x * head * 0.6f, base.y + side.y * head * 0.6f)
        lineTo(base.x - side.x * head * 0.6f, base.y - side.y * head * 0.6f)
        close()
    }
    drawPath(path, ARROW)
}
