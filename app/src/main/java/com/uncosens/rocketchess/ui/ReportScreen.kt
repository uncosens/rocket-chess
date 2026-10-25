package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.Axis
import com.uncosens.rocketchess.analysis.AxisScore
import com.uncosens.rocketchess.analysis.Report
import com.uncosens.rocketchess.analysis.ReportPolicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Five skill scores from many analyzed games, as a radar chart and a list. */
@Composable
fun ReportScreen(vm: ReportViewModel, onPuzzles: (Axis) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        TopBar(stringResource(R.string.report_title), onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = vm.state) {
                ReportState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ReportState.NoIds -> Note(stringResource(R.string.report_no_ids))
                is ReportState.NotEnough -> {
                    Note(stringResource(R.string.report_not_enough, s.games, ReportPolicy.MIN_GAMES))
                    LinearProgressIndicator({ s.games / ReportPolicy.MIN_GAMES.toFloat() }, Modifier.fillMaxWidth())
                }
                is ReportState.Ready -> Ready(s.report, onPuzzles)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Ready(r: Report, onPuzzles: (Axis) -> Unit) {
    Radar(r.axes, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f))
    for (a in r.axes) AxisRow(a, r) { onPuzzles(a.axis) }
    Note(stringResource(R.string.report_footnote))
}

val AXIS_NAMES = mapOf(
    Axis.Tactics to R.string.axis_tactics,
    Axis.Positional to R.string.axis_positional,
    Axis.Opening to R.string.axis_opening,
    Axis.Endgame to R.string.axis_endgame,
    Axis.Defense to R.string.axis_defense,
)

/** Lichess puzzle themes behind each axis's Puzzles button. Lichess has no positional theme; quiet moves come closest. */
val AXIS_THEMES = mapOf(
    Axis.Tactics to setOf("fork", "pin", "skewer", "discoveredAttack", "deflection", "attraction", "hangingPiece", "sacrifice", "doubleCheck"),
    Axis.Positional to setOf("quietMove"),
    Axis.Opening to setOf("opening"),
    Axis.Endgame to setOf("endgame"),
    Axis.Defense to setOf("defensiveMove"),
)

private val AXIS_NOTES = mapOf(
    Axis.Tactics to R.string.axis_tactics_note,
    Axis.Positional to R.string.axis_positional_note,
    Axis.Opening to R.string.axis_opening_note,
    Axis.Endgame to R.string.axis_endgame_note,
    Axis.Defense to R.string.axis_defense_note,
)

/** Name and score, then what it measures and how many samples it has. */
@Composable
private fun AxisRow(a: AxisScore, r: Report, onPuzzles: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(AXIS_NAMES.getValue(a.axis)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (a.score != null) {
                    Text("${a.score.roundToInt()}", fontFamily = Mono, fontWeight = FontWeight.SemiBold)
                    Text("  ${a.low!!.roundToInt()}–${a.high!!.roundToInt()}", fontFamily = Mono, color = muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.report_too_few, a.samples, a.needed), color = muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(AXIS_NOTES.getValue(a.axis)), Modifier.weight(1f), color = muted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onPuzzles) { Text(stringResource(R.string.report_puzzles)) }
            }
            val counts = buildList {
                add(stringResource(R.string.report_samples, a.samples, a.games))
                if (a.axis == Axis.Tactics || a.axis == Axis.Defense) add(stringResource(R.string.report_found, a.found, a.chances))
                if (a.axis == Axis.Defense) add(stringResource(R.string.report_king_errors, r.kingErrors, r.movesBeforeEndgame))
            }
            Text(counts.joinToString(" · "), color = muted, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
        }
    }
}

/**
 * Five spokes, 0 at the center and 100 at the rim. Scored axes get a point and dashed rings for the
 * interval; an axis without enough data gets a dashed spoke and "?", never a point at 0.
 */
@Composable
private fun Radar(axes: List<AxisScore>, modifier: Modifier) {
    val text = rememberTextMeasurer()
    val names = axes.map { stringResource(AXIS_NAMES.getValue(it.axis)) }
    val line = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 44.dp.toPx()
        fun at(i: Int, v: Double): Offset {
            val angle = -PI / 2 + 2 * PI * i / axes.size
            val d = radius * (v / 100).toFloat()
            return Offset(c.x + d * cos(angle).toFloat(), c.y + d * sin(angle).toFloat())
        }
        fun ring(values: List<Double?>): Path = Path().apply {
            values.withIndex().filter { it.value != null }.forEachIndexed { k, (i, v) ->
                val p = at(i, v!!)
                if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        for (level in listOf(25.0, 50.0, 75.0, 100.0)) drawPath(ring(List(axes.size) { level }), line, style = Stroke(1.dp.toPx()))
        axes.forEachIndexed { i, a ->
            val dash = if (a.score == null) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())) else null
            drawLine(line, c, at(i, 100.0), 1.dp.toPx(), pathEffect = dash)
        }
        val scored = axes.count { it.score != null }
        if (scored >= 3) {
            // The interval: thin dashed rings through the low and high ends.
            val band = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            drawPath(ring(axes.map { it.low }), accent.copy(alpha = 0.6f), style = band)
            drawPath(ring(axes.map { it.high }), accent.copy(alpha = 0.6f), style = band)
            drawPath(ring(axes.map { it.score }), accent.copy(alpha = 0.22f))
            drawPath(ring(axes.map { it.score }), accent, style = Stroke(2.dp.toPx()))
        }
        axes.forEachIndexed { i, a -> a.score?.let { drawCircle(accent, 4.dp.toPx(), at(i, it)) } }
        // Labels just outside the rim: the name, then the score or "?".
        axes.forEachIndexed { i, a ->
            val value = a.score?.roundToInt()?.toString() ?: "?"
            val layout = text.measure("${names[i]}\n$value", TextStyle(fontSize = 12.sp, color = if (a.score != null) ink else muted, textAlign = TextAlign.Center))
            val p = at(i, 100.0 * (radius + 24.dp.toPx()) / radius)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
        }
    }
}
