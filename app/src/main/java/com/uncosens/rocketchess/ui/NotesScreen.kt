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

/** The user's Mistakes and Blunders as puzzles, each followed by a Lichess puzzle of the same kind. */
@Composable
fun NotesScreen(vm: NotesViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.load() }
    val drills = vm.drills
    val drill = vm.drill
    val title = stringResource(R.string.notes_title)
    val subtitle = drills?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.notes_progress, vm.index + 1, it.size, it.count { d -> d.key in vm.solved }) }

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
                    TopBar(title, onBack, subtitle)
                    Panel(vm, drill)
                }
            }
        } else {
            // Same board size rule as the analysis screen: 90% of the screen width.
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(title, onBack, subtitle)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Board(vm, drill, Modifier.size(side)) }
                Panel(vm, drill)
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
        arrow = vm.arrow?.let { it.substring(0, 2) to it.substring(2, 4) },
        selected = input.selected,
        targets = input.targets,
        onTap = input::tap,
    )
}

/** Where the puzzle comes from, the task, the result and the buttons. */
@Composable
private fun Panel(vm: NotesViewModel, drill: Drill) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val note = drill.note
    val puzzle = drill.puzzle
    val theme = (note?.mistake?.theme ?: puzzle?.themes?.firstOrNull { it in THEME_NAMES })?.let { stringResource(THEME_NAMES.getValue(it)) }
    if (note != null) {
        val m = note.mistake
        Text(stringResource(R.string.players, note.white, note.black) + " · " + note.date, style = MaterialTheme.typography.bodySmall, color = muted)
        Text(stringResource(R.string.notes_task, GameLine.san(m.fen, m.played), m.cls.label()), style = MaterialTheme.typography.bodyLarge)
        theme?.let { Text(stringResource(R.string.notes_theme, it), style = MaterialTheme.typography.bodySmall, color = muted) }
    } else if (puzzle != null) {
        Text(stringResource(R.string.notes_puzzle, puzzle.rating) + (theme?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(stringResource(if (drill.userWhite) R.string.notes_find_white else R.string.notes_find_black), style = MaterialTheme.typography.bodyLarge)
    }
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
        Button(onClick = vm::next, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.notes_next)) }
    }
}

/** Names for the Lichess themes the puzzle set keeps. */
private val THEME_NAMES = mapOf(
    "mateIn1" to R.string.theme_mate_in_1,
    "mateIn2" to R.string.theme_mate_in_2,
    "mateIn3" to R.string.theme_mate_in_3,
    "mateIn4" to R.string.theme_mate_in_4,
    "mateIn5" to R.string.theme_mate_in_5,
    "mate" to R.string.theme_mate,
    "fork" to R.string.theme_fork,
    "hangingPiece" to R.string.theme_hanging_piece,
    "pin" to R.string.theme_pin,
    "skewer" to R.string.theme_skewer,
    "discoveredAttack" to R.string.theme_discovered_attack,
    "doubleCheck" to R.string.theme_double_check,
    "sacrifice" to R.string.theme_sacrifice,
    "deflection" to R.string.theme_deflection,
    "attraction" to R.string.theme_attraction,
    "endgame" to R.string.theme_endgame,
)

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
