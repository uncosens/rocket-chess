package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.analysis.EngineOptionsStore
import kotlin.math.roundToInt

private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/** Piece set, plus threads, hash size and search depth for the next analysis. Saved on each change. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var opts by remember { mutableStateOf(EngineOptionsStore.load(context)) }
    val set = { o: EngineOptions -> opts = o; EngineOptionsStore.save(context, o) }
    val maxThreads = EngineOptions.maxThreads(EngineOptionsStore.cores)
    var pieces by remember { mutableStateOf(PieceSetStore.load(context)) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(stringResource(R.string.settings_title), onBack)

        Text(stringResource(R.string.settings_pieces), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PieceSet.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = pieces == s,
                    onClick = { pieces = s; PieceSetStore.save(context, s) },
                    shape = SegmentedButtonDefaults.itemShape(i, PieceSet.entries.size),
                ) { Text(s.id, maxLines = 1) }
            }
        }
        ChessBoard(START_FEN, null, null, stringResource(R.string.board_start), Modifier.widthIn(max = 240.dp), pieceSet = pieces)

        Text(stringResource(R.string.engine_settings), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.settings_threads), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow {
            (1..maxThreads).forEach { n ->
                SegmentedButton(
                    selected = opts.threads == n,
                    onClick = { set(opts.copy(threads = n)) },
                    shape = SegmentedButtonDefaults.itemShape(n - 1, maxThreads),
                ) { Text("$n") }
            }
        }

        Text(stringResource(R.string.settings_hash), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow {
            EngineOptions.HASH_CHOICES.forEachIndexed { i, mb ->
                SegmentedButton(
                    selected = opts.hashMb == mb,
                    onClick = { set(opts.copy(hashMb = mb)) },
                    shape = SegmentedButtonDefaults.itemShape(i, EngineOptions.HASH_CHOICES.size),
                ) { Text(stringResource(R.string.settings_hash_mb, mb)) }
            }
        }

        val range = EngineOptions.DEPTH_RANGE
        Text(stringResource(R.string.settings_depth, opts.depth), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = opts.depth.toFloat(),
            onValueChange = { it.roundToInt().let { d -> if (d != opts.depth) set(opts.copy(depth = d)) } },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.widthIn(max = 480.dp),
        )

        Text(stringResource(R.string.settings_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { set(EngineOptions().clamped(EngineOptionsStore.cores)) }) { Text(stringResource(R.string.settings_reset)) }
    }
}
