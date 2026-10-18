package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.EngineOptions
import com.uncosens.rocketchess.analysis.EngineOptionsStore
import kotlin.math.roundToInt

private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/** Piece set, plus threads, hash size and search depth for the next analysis. Saved on each change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var opts by remember { mutableStateOf(EngineOptionsStore.load(context)) }
    val set = { o: EngineOptions -> opts = o; EngineOptionsStore.save(context, o) }
    val maxThreads = EngineOptions.maxThreads(EngineOptionsStore.cores)
    var pieces by remember { mutableStateOf(PieceSetStore.load(context)) }
    var board by remember { mutableStateOf(BoardStyleStore.load(context)) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(stringResource(R.string.settings_title), onBack)

        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        val themes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            themes.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = ThemeStore.mode == m,
                    onClick = { ThemeStore.save(context, m) },
                    shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                ) { Text(stringResource(label)) }
            }
        }

        Text(stringResource(R.string.settings_pieces), style = MaterialTheme.typography.titleMedium)
        // Always two rows of equal chips, with small text so every name fits.
        Column {
            for (row in PieceSet.entries.chunked((PieceSet.entries.size + 1) / 2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in row) {
                        // A plain chip with little padding, so long names like "Chessnut" fit.
                        val selected = pieces == s
                        Surface(
                            onClick = { pieces = s; PieceSetStore.save(context, s) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp).semantics { this.selected = selected },
                        ) {
                            Text(
                                s.id.replaceFirstChar { it.uppercase() },
                                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.settings_board), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardStyle.entries.forEach { s ->
                BoardSwatch(s, selected = board == s) { board = s; BoardStyleStore.save(context, s) }
            }
        }
        ChessBoard(
            START_FEN, null, null, stringResource(R.string.board_start), Modifier.widthIn(max = 240.dp),
            pieceSet = pieces, boardStyle = board,
        )

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
        TextButton(onClick = onLicenses) { Text(stringResource(R.string.about_licenses)) }
    }
}

/** A 2x2 corner of a board style with its name. */
@Composable
private fun BoardSwatch(style: BoardStyle, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val texture = remember(style) { BoardStyleStore.texture(context, style) }
    val theme = LocalBoardColors.current
    val light = if (style == BoardStyle.ROCKET) theme.light else style.light
    val dark = if (style == BoardStyle.ROCKET) theme.dark else style.dark
    val name = style.id.replace('-', ' ').replaceFirstChar { it.uppercase() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))) {
                val half = size.width / 2
                if (texture != null) {
                    // The texture's top-left quarter is its top-left 2x2 squares.
                    drawImage(
                        texture,
                        srcSize = IntSize(texture.width / 4, texture.height / 4),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                } else {
                    for (i in 0 until 4) {
                        drawRect(if (i == 0 || i == 3) light else dark, Offset((i % 2) * half, (i / 2) * half), Size(half, half))
                    }
                }
            }
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
    }
}
