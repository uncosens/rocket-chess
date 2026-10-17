package com.uncosens.rocketchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

fun MoveClass.color(): Color = when (this) {
    MoveClass.Best -> Color(0xFF2E9E6B)
    MoveClass.Excellent -> Color(0xFF5BAE8C)
    MoveClass.Good -> Color(0xFF8AA7B5)
    MoveClass.Inaccuracy -> Color(0xFFE0B23A)
    MoveClass.Mistake -> Color(0xFFE5873A)
    MoveClass.Blunder -> Color(0xFFE5484D)
}

fun MoveClass.suffix(): String = when (this) {
    MoveClass.Inaccuracy -> "?!"
    MoveClass.Mistake -> "?"
    MoveClass.Blunder -> "??"
    else -> ""
}

@Composable
fun MoveClass.label(): String = stringResource(
    when (this) {
        MoveClass.Best -> R.string.class_best
        MoveClass.Excellent -> R.string.class_excellent
        MoveClass.Good -> R.string.class_good
        MoveClass.Inaccuracy -> R.string.class_inaccuracy
        MoveClass.Mistake -> R.string.class_mistake
        MoveClass.Blunder -> R.string.class_blunder
    },
)

/** "+0.35" or "#3" / "#-3", from White's view. */
fun Eval.text(): String = when (this) {
    is Eval.Cp -> (if (cp >= 0) "+" else "-") + "%.2f".format(abs(cp) / 100.0)
    is Eval.Mate -> if (whiteWins) "#$moves" else "#-$moves"
}

/** Start button, progress and stop, or the result line. */
@Composable
fun AnalysisControls(vm: GameViewModel, onAnalyze: () -> Unit) {
    when (val s = vm.analysis) {
        is AnalysisState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.analyzing, s.done, s.total))
                OutlinedButton(onClick = vm::stopAnalysis) { Text(stringResource(R.string.stop)) }
            }
            LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAnalyze) {
                Text(stringResource(if (s is AnalysisState.Idle) R.string.analyze else R.string.analyze_again))
            }
            when (s) {
                is AnalysisState.Stopped -> Text(stringResource(R.string.analysis_stopped, s.done, s.total))
                is AnalysisState.Done -> Text(
                    stringResource(R.string.analysis_done, s.settings.engine, s.settings.depth, s.settings.multiPv, s.settings.threads, s.settings.hashMb),
                    style = MaterialTheme.typography.bodySmall,
                )
                is AnalysisState.Failed -> Text(stringResource(R.string.analysis_failed, s.message), color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
    }
}

/** Eval and grade for the selected position and the move that led to it. */
@Composable
fun SelectedMoveInfo(vm: GameViewModel, line: GameLine) {
    val e = vm.evals.getOrNull(vm.ply) ?: return
    val parts = mutableListOf(stringResource(R.string.eval_line, e.eval.text(), e.depth))
    if (vm.ply > 0) {
        vm.grade(vm.ply - 1)?.let { g ->
            parts += g.cls.label()
            val before = vm.evals[vm.ply - 1]
            if (g.cls != MoveClass.Best && before?.best != null) {
                parts += stringResource(R.string.engine_move, GameLine.san(line.positions[vm.ply - 1].fen, before.best))
            }
        }
    }
    Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
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
        )
    }
}

/** White's win% over the game. Tap to jump to a position. */
@Composable
fun EvalGraph(evals: List<PositionEval?>, ply: Int, onSelect: (Int) -> Unit) {
    if (evals.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val desc = stringResource(R.string.graph_description)
    Canvas(
        Modifier.fillMaxWidth().height(96.dp)
            .semantics { contentDescription = desc }
            .pointerInput(evals.size) {
                detectTapGestures { onSelect((it.x / size.width * (evals.size - 1)).roundToInt()) }
            },
    ) {
        val step = size.width / (evals.size - 1)
        fun y(e: PositionEval) = size.height * (1 - whiteWinPercent(e.eval).toFloat() / 100f)
        drawRect(Color(0xFF2B2B2B))
        // Analysis runs in order, so the known part is a prefix.
        val known = evals.takeWhile { it != null }.filterNotNull()
        val fill = Path().apply {
            moveTo(0f, size.height)
            known.forEachIndexed { i, e -> lineTo(i * step, y(e)) }
            lineTo((known.size - 1).coerceAtLeast(0) * step, size.height)
            close()
        }
        drawPath(fill, Color(0xFFE8E8E8))
        drawLine(Color.Gray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        drawLine(line, Offset(ply * step, 0f), Offset(ply * step, size.height), strokeWidth = 2.dp.toPx())
        drawRect(line, style = Stroke(1.dp.toPx()))
    }
}
