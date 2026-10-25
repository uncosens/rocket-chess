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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.MoveClass
import com.uncosens.rocketchess.chess.GameLine

/** The user's Mistakes and Blunders as puzzles: find a better move than the one played. */
@Composable
fun NotesScreen(vm: NotesViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.load() }
    val notes = vm.notes
    val note = vm.note
    val title = stringResource(R.string.notes_title)
    val subtitle = notes?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.notes_progress, vm.index + 1, it.size, it.count { n -> n.mistake.key in vm.solved }) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (note == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(title, onBack)
                if (notes == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else Hint(stringResource(R.string.notes_empty))
                if (vm.noIds) Hint(stringResource(R.string.notes_no_ids))
            }
        } else if (maxWidth > maxHeight) {
            val side = maxHeight - 16.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Puzzle(vm, note, Modifier.padding(top = 8.dp).size(side))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopBar(title, onBack, subtitle)
                    Panel(vm, note)
                }
            }
        } else {
            // Same board size rule as the analysis screen: 90% of the screen width.
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(title, onBack, subtitle)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Puzzle(vm, note, Modifier.size(side)) }
                Panel(vm, note)
            }
        }
    }
}

@Composable
private fun Puzzle(vm: NotesViewModel, note: MistakeNote, modifier: Modifier) {
    val m = note.mistake
    // A new input after each wrong try clears the selected piece.
    val input = key(vm.misses) { rememberMoveInput(m.fen, enabled = vm.attempt == Attempt.Solving || vm.attempt == Attempt.Wrong) { vm.play(it) } }
    ChessBoard(
        fen = m.fen,
        lastMove = m.lastMove,
        check = null,
        description = stringResource(R.string.notes_title),
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        flipped = !note.userWhite,
        arrow = vm.arrow?.let { it.substring(0, 2) to it.substring(2, 4) },
        selected = input.selected,
        targets = input.targets,
        onTap = input::tap,
    )
}

/** Game, task, result and buttons. */
@Composable
private fun Panel(vm: NotesViewModel, note: MistakeNote) {
    val m = note.mistake
    Text(stringResource(R.string.players, note.white, note.black) + " · " + note.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.notes_task, GameLine.san(m.fen, m.played), m.cls.label()), style = MaterialTheme.typography.bodyLarge)
    when (vm.attempt) {
        Attempt.Solving -> {}
        Attempt.Wrong -> Text(stringResource(R.string.notes_wrong, vm.tried.orEmpty()), color = m.cls.color(), fontWeight = FontWeight.Medium)
        Attempt.Solved -> Text(stringResource(R.string.notes_solved), color = MoveClass.Best.color(), fontWeight = FontWeight.Medium)
        Attempt.Shown -> Text(stringResource(R.string.notes_answer, m.answers.joinToString(" / ") { GameLine.san(m.fen, it) }), fontWeight = FontWeight.Medium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val done = vm.attempt == Attempt.Solved || vm.attempt == Attempt.Shown
        OutlinedButton(onClick = vm::showAnswer, enabled = !done, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.notes_show)) }
        Button(onClick = vm::next, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.notes_next)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
