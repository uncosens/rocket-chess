package com.uncosens.rocketchess.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.MoveClass
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.engine.StockfishSession

@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val exe = remember { StockfishSession.binary(context) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadFile(context.contentResolver, it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        val wide = maxWidth > maxHeight
        val side = maxHeight
        val board = @Composable { m: Modifier -> Board(vm, m) }
        val panel = @Composable { m: Modifier ->
            Panel(
                vm,
                onPaste = { pasting = true },
                onOpen = { openFile.launch(arrayOf("*/*")) },
                onAnalyze = { vm.analyze(exe) },
                modifier = m,
            )
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                board(Modifier.size(side))
                panel(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                board(Modifier.fillMaxWidth())
                panel(Modifier.weight(1f))
            }
        }
    }

    if (pasting) PasteDialog(onDismiss = { pasting = false }, onLoad = { pasting = false; vm.load(it) })
}

@Composable
private fun Board(vm: GameViewModel, modifier: Modifier) {
    val line = vm.line
    val position = line?.positions?.get(vm.ply)
    val last = line?.plies?.getOrNull(vm.ply - 1)
    ChessBoard(
        fen = position?.fen ?: GameLine.START_FEN,
        lastMove = last?.let { it.from to it.to },
        check = position?.check,
        description = if (last == null) stringResource(R.string.board_start)
        else stringResource(R.string.board_after, moveLabel(line!!, vm.ply - 1)),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panel(vm: GameViewModel, onPaste: () -> Unit, onOpen: () -> Unit, onAnalyze: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPaste) { Text(stringResource(R.string.paste_pgn)) }
            FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.open_file)) }
        }
        vm.error?.let { ErrorText(it) }
        val line = vm.line
        if (line == null) {
            Text(stringResource(R.string.no_game), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        vm.game?.tags?.let { tags ->
            Text(
                stringResource(R.string.players, tags["White"] ?: "?", tags["Black"] ?: "?") +
                    (vm.game?.result?.let { "  $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        NavButtons(vm, line.plies.size)
        AnalysisControls(vm, onAnalyze)
        EvalGraph(vm.evals, vm.ply, vm::goTo)
        SelectedMoveInfo(vm, line)
        if (vm.evals.isNotEmpty()) Summary(vm, line)
        FlowRow(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            line.plies.forEachIndexed { i, _ ->
                val selected = vm.ply == i + 1
                val cls = vm.grade(i)?.cls
                val label = moveLabel(line, i)
                val desc = cls?.let { stringResource(R.string.move_with_class, label, it.label()) } ?: label
                TextButton(
                    onClick = { vm.goTo(i + 1) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desc },
                ) {
                    Text(
                        label + (cls?.suffix() ?: ""),
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            cls != null && cls >= MoveClass.Inaccuracy -> cls.color()
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavButtons(vm: GameViewModel, plies: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("⏮", R.string.nav_first, 0),
            Triple("◀", R.string.nav_prev, vm.ply - 1),
            Triple("▶", R.string.nav_next, vm.ply + 1),
            Triple("⏭", R.string.nav_last, plies),
        ).forEach { (symbol, label, target) ->
            val desc = stringResource(label)
            FilledTonalButton(onClick = { vm.goTo(target) }, modifier = Modifier.semantics { contentDescription = desc }) {
                Text(symbol)
            }
        }
    }
}

@Composable
private fun ErrorText(error: LoadError) {
    val text = when (error) {
        is LoadError.Parse -> stringResource(R.string.error_pgn, error.e.line, error.e.column, error.e.reason)
        LoadError.TooLarge -> stringResource(R.string.error_too_large, GameViewModel.MAX_BYTES / 1024 / 1024)
        LoadError.Unreadable -> stringResource(R.string.error_unreadable)
    }
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onLoad: (String) -> Unit) {
    // Not saveable: a big PGN would overflow the saved-state bundle.
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_pgn)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.pgn_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onLoad(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.load)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "12. Nf3" for White, "12... Nc6" for Black. Numbers come from the start FEN. */
private fun moveLabel(line: GameLine, plyIndex: Int): String {
    val fields = line.positions[0].fen.split(' ')
    val offset = plyIndex + if (fields.getOrNull(1) == "b") 1 else 0
    val n = (fields.getOrNull(5)?.toIntOrNull() ?: 1) + offset / 2
    val san = line.plies[plyIndex].san
    return if (offset % 2 == 0) "$n. $san" else "$n... $san"
}
