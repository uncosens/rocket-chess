package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.MoveClass
import com.uncosens.rocketchess.analysis.Puzzles
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Setup

/** The user's Mistakes and Blunders as puzzles, mixed with Lichess puzzles of the same kinds. Load with [NotesViewModel.load]. */
@Composable
fun NotesScreen(vm: NotesViewModel, onAnalyze: (pgn: String, userWhite: Boolean) -> Unit, onBack: () -> Unit, title: String = stringResource(R.string.notes_title)) {
    BackHandler(onBack = onBack)
    val drills = vm.drills
    val drill = vm.drill
    var settings by remember { mutableStateOf(false) }
    val bar = @Composable {
        TopBar(title, onBack, action = { SquareIconButton(R.drawable.ic_settings, stringResource(R.string.notes_settings), { settings = true }) })
    }
    if (settings) SettingsDialog(vm) { settings = false }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (drill == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                bar()
                if (drills == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else if (vm.checking == 0) Hint(stringResource(R.string.notes_empty))
                if (vm.checking > 0) Hint(stringResource(R.string.notes_checking, vm.checking))
                if (vm.noIds) Hint(stringResource(R.string.notes_no_ids))
            }
        } else if (maxWidth > maxHeight) {
            val side = maxHeight - 16.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Board(vm, drill, Modifier.padding(top = 8.dp).size(side))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bar()
                    Panel(vm, drill, onAnalyze)
                }
            }
        } else {
            // Same board size rule as the analysis screen: 90% of the screen width.
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                bar()
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Board(vm, drill, Modifier.size(side)) }
                Panel(vm, drill, onAnalyze)
            }
        }
    }
}

@Composable
private fun Board(vm: NotesViewModel, drill: Drill, modifier: Modifier) {
    // A position of the game, while stepping back through it.
    val past = vm.historyPly?.let { h -> drill.note?.line?.let { it.positions[h] to it.plies.getOrNull(h - 1) } }
    val open = (vm.attempt == Attempt.Solving || vm.attempt == Attempt.Wrong || vm.attempt == Attempt.Going) && !vm.replying && past == null
    // A new input after each wrong try clears the selected piece.
    val input = key(vm.misses) { rememberMoveInput(vm.fen, enabled = open) { vm.play(it) } }
    ChessBoard(
        fen = past?.first?.fen ?: vm.fen,
        lastMove = if (past != null) past.second?.let { it.from to it.to } else vm.lastMove,
        check = if (past != null) past.first.check else vm.check,
        description = stringResource(R.string.notes_title),
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        flipped = !drill.userWhite,
        grade = MoveClass.Best.takeIf { vm.right && past == null },
        arrow = vm.arrow?.takeIf { past == null }?.let { it.substring(0, 2) to it.substring(2, 4) },
        selected = input.selected,
        targets = input.targets,
        onTap = input::tap,
    )
}

/** Whose puzzle it is, the task, the result and the buttons. */
@Composable
private fun Panel(vm: NotesViewModel, drill: Drill, onAnalyze: (pgn: String, userWhite: Boolean) -> Unit) {
    val kind = drill.puzzle?.let { stringResource(R.string.notes_puzzle_rating, Puzzles.toChessCom(it.rating)) } ?: stringResource(R.string.notes_mine)
    Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(if (drill.userWhite) R.string.notes_find_white else R.string.notes_find_black), style = MaterialTheme.typography.bodyLarge)
    when (vm.attempt) {
        Attempt.Solving -> {}
        Attempt.Wrong -> Text(stringResource(R.string.notes_wrong), color = MoveClass.Mistake.color(), fontWeight = FontWeight.Medium)
        Attempt.Going -> Text(stringResource(R.string.notes_going), color = MoveClass.Best.color(), fontWeight = FontWeight.Medium)
        Attempt.Solved -> Text(stringResource(R.string.notes_solved), color = MoveClass.Best.color(), fontWeight = FontWeight.Medium)
        Attempt.Shown -> {
            // At the first move, every move that counts.
            val moves = if (vm.fen == drill.fen) drill.firstAnswers else listOfNotNull(vm.arrow)
            Text(stringResource(R.string.notes_answer, moves.joinToString(" / ") { GameLine.san(vm.fen, it) }), fontWeight = FontWeight.Medium)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val done = vm.attempt == Attempt.Solved || vm.attempt == Attempt.Shown
        OutlinedButton(onClick = vm::showAnswer, enabled = !done && !vm.replying, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.notes_show)) }
        // A Lichess puzzle opens from its start with its moves; a mistake at the shown position.
        val pgn = drill.puzzle?.let { Setup.pgn(it.fen, it.moves) } ?: Setup.pgn(vm.fen)
        Button(onClick = { onAnalyze(pgn, drill.userWhite) }, enabled = !vm.replying, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.analyze)) }
        Button(onClick = vm::next, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = LocalAccents.current.notes.color, contentColor = LocalAccents.current.notes.on)) { Text(stringResource(R.string.notes_next)) }
    }
    // My Game: step through the game that led to the mistake.
    drill.note?.let { n ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            SquareIconButton(R.drawable.ic_back, stringResource(R.string.nav_prev), vm::historyBack, size = 48.dp, enabled = (vm.historyPly ?: n.ply) > 0)
            SquareIconButton(R.drawable.ic_next, stringResource(R.string.nav_next), vm::historyForward, size = 48.dp, enabled = vm.historyPly != null)
        }
    }
}

/** Puzzle rating (Chess.com scale) and which puzzles to show. */
@Composable
private fun SettingsDialog(vm: NotesViewModel, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(vm.chosenRating > 0) }
    var rating by remember { mutableIntStateOf(if (vm.chosenRating > 0) vm.chosenRating else 1200) }
    var mix by remember { mutableStateOf(vm.mix) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.notes_difficulty), style = MaterialTheme.typography.titleSmall)
                Choices(listOf(R.string.notes_auto, R.string.notes_custom), if (custom) 1 else 0) { custom = it == 1 }
                if (custom) {
                    Text(stringResource(R.string.notes_rating, rating), fontFamily = Mono)
                    Slider(rating.toFloat(), { rating = (it / 50).toInt() * 50 }, valueRange = 400f..2500f)
                }
                // Report puzzles are always Lichess puzzles.
                if (vm.only == null) {
                    Text(stringResource(R.string.notes_include), style = MaterialTheme.typography.titleSmall)
                    Choices(listOf(R.string.notes_include_mine, R.string.notes_include_puzzles, R.string.notes_include_both), mix.ordinal) { mix = Mix.entries[it] }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.setSettings(if (custom) rating else 0, mix); onDismiss() }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Choices(labels: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(selected = i == selected, onClick = { onPick(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
