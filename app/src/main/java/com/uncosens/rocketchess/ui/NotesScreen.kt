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
import com.uncosens.rocketchess.chess.Setup

/** The user's Mistakes and Blunders as puzzles, mixed with Lichess puzzles of the same kinds. Load with [NotesViewModel.load]. */
@Composable
fun NotesScreen(vm: NotesViewModel, onAnalyze: (pgn: String) -> Unit, onBack: () -> Unit, title: String = stringResource(R.string.notes_title)) {
    BackHandler(onBack = onBack)
    val drills = vm.drills
    val drill = vm.drill

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (drill == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(title, onBack)
                if (drills == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else Hint(stringResource(R.string.notes_empty))
                if (vm.noIds) Hint(stringResource(R.string.notes_no_ids))
            }
        } else if (maxWidth > maxHeight) {
            val side = maxHeight - 16.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Board(vm, drill, Modifier.padding(top = 8.dp).size(side))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopBar(title, onBack)
                    Panel(vm, drill, onAnalyze)
                }
            }
        } else {
            // Same board size rule as the analysis screen: 90% of the screen width.
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(title, onBack)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Board(vm, drill, Modifier.size(side)) }
                Panel(vm, drill, onAnalyze)
            }
        }
    }
}

@Composable
private fun Board(vm: NotesViewModel, drill: Drill, modifier: Modifier) {
    val open = (vm.attempt == Attempt.Solving || vm.attempt == Attempt.Wrong || vm.attempt == Attempt.Going) && !vm.replying
    // A new input after each wrong try clears the selected piece.
    val input = key(vm.misses) { rememberMoveInput(vm.fen, enabled = open) { vm.play(it) } }
    ChessBoard(
        fen = vm.fen,
        lastMove = vm.lastMove,
        check = vm.check,
        description = stringResource(R.string.notes_title),
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        flipped = !drill.userWhite,
        grade = MoveClass.Best.takeIf { vm.right },
        arrow = vm.arrow?.let { it.substring(0, 2) to it.substring(2, 4) },
        selected = input.selected,
        targets = input.targets,
        onTap = input::tap,
    )
}

/** Whose puzzle it is, the task, the result and the buttons. */
@Composable
private fun Panel(vm: NotesViewModel, drill: Drill, onAnalyze: (pgn: String) -> Unit) {
    Text(stringResource(if (drill.note != null) R.string.notes_mine else R.string.notes_puzzle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(if (drill.userWhite) R.string.notes_find_white else R.string.notes_find_black), style = MaterialTheme.typography.bodyLarge)
    when (vm.attempt) {
        Attempt.Solving -> {}
        Attempt.Wrong -> Text(stringResource(R.string.notes_wrong, vm.tried.orEmpty()), color = MoveClass.Mistake.color(), fontWeight = FontWeight.Medium)
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
        // The shown position, opened for analysis.
        OutlinedButton(onClick = { onAnalyze(Setup.pgn(vm.fen)) }, enabled = !vm.replying, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.analyze)) }
        Button(onClick = vm::next, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.notes_next)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
