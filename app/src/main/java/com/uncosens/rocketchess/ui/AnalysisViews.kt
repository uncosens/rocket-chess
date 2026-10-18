package com.uncosens.rocketchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.Eval
import com.uncosens.rocketchess.analysis.MoveClass
import com.uncosens.rocketchess.analysis.PositionEval
import com.uncosens.rocketchess.analysis.whiteWinPercent
import com.uncosens.rocketchess.chess.GameLine
import kotlin.math.abs
import kotlin.math.roundToInt

private val GRAPH_BG = Color(0xFF10162A)
private val GRAPH_FILL = Color(0xFFE8ECF4)
private val GRAPH_MID = Color(0xFF59678A)

fun MoveClass.color(): Color = when (this) {
    MoveClass.Brilliant -> Color(0xFF1FBFA9)
    MoveClass.Best -> Color(0xFF1C93A8)
    MoveClass.Excellent -> Color(0xFF4C8DF6)
    MoveClass.Good -> Color(0xFF8C96AB)
    MoveClass.Inaccuracy -> Color(0xFFE0A21F)
    MoveClass.Mistake -> Color(0xFFF07B2A)
    MoveClass.Blunder -> Color(0xFFD9363E)
}

fun MoveClass.suffix(): String = when (this) {
    MoveClass.Brilliant -> "!!"
    MoveClass.Inaccuracy -> "?!"
    MoveClass.Mistake -> "?"
    MoveClass.Blunder -> "??"
    else -> ""
}

/** Text for the round grade badge, or null for no badge. */
fun MoveClass.badge(): String? = when (this) {
    MoveClass.Best -> "★"
    MoveClass.Excellent, MoveClass.Good -> null
    else -> suffix()
}

@Composable
fun MoveClass.label(): String = stringResource(
    when (this) {
        MoveClass.Brilliant -> R.string.class_brilliant
        MoveClass.Best -> R.string.class_best
        MoveClass.Excellent -> R.string.class_excellent
        MoveClass.Good -> R.string.class_good
        MoveClass.Inaccuracy -> R.string.class_inaccuracy
        MoveClass.Mistake -> R.string.class_mistake
        MoveClass.Blunder -> R.string.class_blunder
    },
)

/** "+0.35" or "M3" / "-M3", from White's view, or Black's when [whiteView] is false. */
fun Eval.text(whiteView: Boolean = true): String = when (this) {
    is Eval.Cp -> {
        val v = if (whiteView) cp else -cp
        (if (v >= 0) "+" else "-") + "%.2f".format(abs(v) / 100.0)
    }
    // "M3" = mate in 3 for the viewing side, "-M3" against it, "#" once mated.
    is Eval.Mate -> if (moves == 0) "#" else if (whiteWins == whiteView) "M$moves" else "-M$moves"
}

/** Stop while running; Analyze to start or go on after a stop. Nothing once done. */
@Composable
fun AnalyzeButton(vm: GameViewModel, onAnalyze: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    when (vm.analysis) {
        is AnalysisState.Running -> OutlinedButton(onClick = vm::stopAnalysis, modifier = modifier, shape = shape) {
            Text(stringResource(R.string.stop))
        }
        is AnalysisState.Done -> {}
        else -> Button(onClick = onAnalyze, modifier = modifier, shape = shape) { Text(stringResource(R.string.analyze)) }
    }
}

/** Progress while running, or the result line. Nothing before the first run. */
@Composable
fun AnalysisStatus(vm: GameViewModel) {
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val s = vm.analysis) {
        is AnalysisState.Running -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.analyzing, s.done, s.total), style = small, color = muted)
            LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)))
        }
        is AnalysisState.Stopped -> Text(stringResource(R.string.analysis_stopped, s.done, s.total), style = small, color = muted)
        is AnalysisState.Done -> Text(
            stringResource(R.string.analysis_done, s.settings.engine, s.settings.depth, s.settings.multiPv, s.settings.threads, s.settings.hashMb),
            style = small,
            color = muted,
        )
        is AnalysisState.Failed -> Text(stringResource(R.string.analysis_failed, s.message), style = small, color = MaterialTheme.colorScheme.error)
        AnalysisState.Idle -> {}
    }
}

/** Counts of inaccuracies, mistakes and blunders per side. */
@Composable
fun Summary(vm: GameViewModel, line: GameLine) {
    val white = line.positions[0].fen.split(' ')[1] == "w"
    val counts = Array(2) { IntArray(3) } // [side][inaccuracy, mistake, blunder]
    for (i in line.plies.indices) {
        val g = vm.grade(i) ?: continue
        val side = if ((i % 2 == 0) == white) 0 else 1
        when (g.cls) {
            MoveClass.Inaccuracy -> counts[side][0]++
            MoveClass.Mistake -> counts[side][1]++
            MoveClass.Blunder -> counts[side][2]++
            else -> {}
        }
    }
    listOf(R.string.side_white, R.string.side_black).forEachIndexed { side, name ->
        val c = counts[side]
        Text(
            stringResource(name) + ": " + listOf(
                pluralStringResource(R.plurals.inaccuracies, c[0], c[0]),
                pluralStringResource(R.plurals.mistakes, c[1], c[1]),
                pluralStringResource(R.plurals.blunders, c[2], c[2]),
            ).joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Card with White's win% over the game and the side counts. Tap to jump to a position. */
@Composable
fun EvalCard(vm: GameViewModel, line: GameLine) {
    if (vm.evals.size < 2) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(stringResource(R.string.eval_title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.tap_to_jump), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EvalGraph(vm.evals, vm.ply, vm::goTo, whiteView = !vm.flipped)
            Summary(vm, line)
        }
    }
}

@Composable
private fun EvalGraph(evals: List<PositionEval?>, ply: Int, onSelect: (Int) -> Unit, whiteView: Boolean) {
    val cursor = MaterialTheme.colorScheme.primary
    val desc = stringResource(R.string.graph_description)
    Canvas(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = desc }
            .pointerInput(evals.size) {
                detectTapGestures { onSelect((it.x / size.width * (evals.size - 1)).roundToInt()) }
            },
    ) {
        val step = size.width / (evals.size - 1)
        // The bottom side's winning chance rises; its area is drawn in its own color.
        fun y(e: PositionEval) = whiteWinPercent(e.eval).toFloat().let { w -> size.height * (1 - (if (whiteView) w else 100 - w) / 100f) }
        drawRect(if (whiteView) GRAPH_BG else GRAPH_FILL)
        // Analysis runs in order, so the known part is a prefix.
        val known = evals.takeWhile { it != null }.filterNotNull()
        val fill = Path().apply {
            moveTo(0f, size.height)
            known.forEachIndexed { i, e -> lineTo(i * step, y(e)) }
            lineTo((known.size - 1).coerceAtLeast(0) * step, size.height)
            close()
        }
        drawPath(fill, if (whiteView) GRAPH_FILL else GRAPH_BG)
        drawLine(GRAPH_MID, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        drawLine(cursor, Offset(ply * step, 0f), Offset(ply * step, size.height), strokeWidth = 2.dp.toPx())
        known.getOrNull(ply)?.let { drawCircle(cursor, 4.dp.toPx(), Offset(ply * step, y(it))) }
    }
}
