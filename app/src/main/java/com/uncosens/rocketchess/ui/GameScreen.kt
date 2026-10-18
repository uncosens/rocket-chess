package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.EngineBudget
import com.uncosens.rocketchess.analysis.EngineOptionsStore
import com.uncosens.rocketchess.analysis.MoveClass
import com.uncosens.rocketchess.chess.Finish
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.engine.StockfishSession

@Composable
fun GameScreen(vm: GameViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val exe = remember { StockfishSession.binary(context) }
    val onAnalyze = { EngineOptionsStore.load(context).let { vm.analyze(exe, it, EngineBudget.forDevice(context, it)) } }
    // A finished run can be redone only once the engine settings differ from the ones it used.
    val opts = EngineOptionsStore.load(context)
    val ran = (vm.analysis as? AnalysisState.Done)?.settings
    val engines = remember(opts, ran) { EngineBudget.forDevice(context, opts) }
    val changed = ran != null && (ran.depth != opts.depth || ran.threads != engines || ran.hashMb != opts.hashMb)
    val onReanalyze = if (changed) ({ vm.analyze(exe, opts, engines, fresh = true) }) else null
    BackHandler(onBack = onBack)
    // A newly opened game is analyzed right away. Keyed on the state too: reopening the same
    // game after a stop gives an equal game but resets the state to Idle.
    LaunchedEffect(vm.game, vm.analysis) { if (vm.game != null && vm.analysis == AnalysisState.Idle) onAnalyze() }
    // Positions the user reaches on the board are analyzed right away.
    LaunchedEffect(vm.variation, vm.ply) { vm.analyzeLive(exe, EngineOptionsStore.load(context)) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (maxWidth > maxHeight) {
            val side = maxHeight - 72.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BoardBlock(vm, Modifier.size(side))
                Column(Modifier.weight(1f)) {
                    Header(vm, onBack)
                    Details(vm, Modifier.weight(1f))
                    Footer(vm, onAnalyze, onReanalyze)
                }
            }
        } else {
            // The board is 90% of the screen width on every phone and tablet; the details below
            // take what is left and scroll if it is not enough.
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column {
                Header(vm, onBack)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { BoardBlock(vm, Modifier.size(side)) }
                Details(vm, Modifier.weight(1f))
                Footer(vm, onAnalyze, onReanalyze)
            }
        }
    }
}

@Composable
private fun Header(vm: GameViewModel, onBack: () -> Unit) {
    val tags = vm.game?.tags
    val title = tags?.let { stringResource(R.string.players, it["White"] ?: "?", it["Black"] ?: "?") } ?: ""
    val subtitle = listOfNotNull(tags?.get("Event")?.takeIf { it.isNotBlank() && it != "?" }, vm.game?.result)
        .joinToString(" · ").ifEmpty { null }
    TopBar(title, onBack, subtitle) {
        SquareIconButton(R.drawable.ic_flip, stringResource(R.string.flip_board), { vm.flipped = !vm.flipped })
    }
}

/** The top player's name, the board, the bottom player's name. */
@Composable
private fun BoardBlock(vm: GameViewModel, modifier: Modifier) {
    val tags = vm.game?.tags
    val black = tags?.get("Black") ?: stringResource(R.string.side_black)
    val white = tags?.get("White") ?: stringResource(R.string.side_white)
    val fen = vm.line?.positions?.getOrNull(vm.ply)?.fen ?: GameLine.START_FEN
    val (whiteEdge, blackEdge) = remember(fen) { materialEdge(fen) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (vm.flipped) PlayerRow(white, true, whiteEdge) else PlayerRow(black, false, blackEdge)
        Board(vm, modifier.clip(RoundedCornerShape(12.dp)))
        if (vm.flipped) PlayerRow(black, false, blackEdge) else PlayerRow(white, true, whiteEdge)
    }
}

/** Color dot, name, then the pieces this side is up and its point lead. */
@Composable
private fun PlayerRow(name: String, white: Boolean, edge: MaterialEdge) {
    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(12.dp).clip(RoundedCornerShape(4.dp))
                .background(if (white) Color.White else Color(0xFF14161B))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        )
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        // U+FE0E asks for the text form of the chess symbols, not emoji.
        if (edge.pieces.isNotEmpty()) Text(edge.pieces.map { GLYPHS.getValue(it) + "\uFE0E" }.joinToString(""), color = muted, fontSize = 16.sp)
        if (edge.points > 0) Text("+${edge.points}", color = muted, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
    }
}

private val GLYPHS = mapOf('q' to "\u265B", 'r' to "\u265C", 'b' to "\u265D", 'n' to "\u265E", 'p' to "\u265F")

@Composable
private fun Board(vm: GameViewModel, modifier: Modifier) {
    val line = vm.line
    val position = line?.positions?.get(vm.ply)
    val last = line?.plies?.getOrNull(vm.ply - 1)
    // Engine's best move in this position, as a UCI move like "e2e4".
    val best = vm.evals.getOrNull(vm.ply)?.best
    val fen = position?.fen ?: GameLine.START_FEN
    // Tap a piece, then a target square, to play your own move.
    val input = rememberMoveInput(fen, enabled = line != null) { vm.play(it) }
    ChessBoard(
        fen = fen,
        lastMove = last?.let { it.from to it.to },
        check = position?.check,
        description = if (last == null) stringResource(R.string.board_start)
        else stringResource(R.string.board_after, moveLabel(line!!, vm.ply - 1)),
        modifier = modifier,
        grade = if (vm.ply > 0) vm.grade(vm.ply - 1)?.cls else null,
        flipped = vm.flipped,
        arrow = best?.let { it.substring(0, 2) to it.substring(2, 4) },
        ending = when {
            position?.mate == true -> Ending(whiteLost = fen.split(' ')[1] == "w", Finish.MATE)
            // Resignation and time loss show only at the end of the game itself, not in the user's own moves.
            vm.variation == null && line != null && vm.ply == line.plies.size ->
                vm.game?.finish()?.let { Ending(whiteLost = (vm.game?.result ?: vm.game?.tags?.get("Result")) == "0-1", it) }
            else -> null
        },
        selected = input.selected,
        targets = input.targets,
        onTap = input::tap,
    )
}

/** Selected move, analysis status and the eval graph. Scrolls when space is short. */
@Composable
private fun Details(vm: GameViewModel, modifier: Modifier) {
    Column(modifier.padding(top = 6.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        vm.error?.let { ErrorText(it) }
        val line = vm.line
        if (line == null) {
            Text(stringResource(R.string.no_game), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        MoveCard(vm, line)
        AnalysisStatus(vm)
        EvalCard(vm, line)
    }
}

/** Grade badge, move, engine move and eval for the selected position. */
@Composable
private fun MoveCard(vm: GameViewModel, line: GameLine) {
    val e = vm.evals.getOrNull(vm.ply)
    val g = if (vm.ply > 0) vm.grade(vm.ply - 1) else null
    val notes = mutableListOf<String>()
    // Best and Brilliant are the engine's own move, so there is no other move to show.
    if (g != null && g.cls != MoveClass.Best && g.cls != MoveClass.Brilliant) {
        vm.evals[vm.ply - 1]?.best?.let { notes += stringResource(R.string.engine_move, GameLine.san(line.positions[vm.ply - 1].fen, it)) }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(g?.cls?.color() ?: MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                when (g?.cls) {
                    null, MoveClass.Good, MoveClass.Fine -> {}
                    MoveClass.Best -> Icon(painterResource(R.drawable.ic_star), null, Modifier.size(16.dp), tint = Color.White)
                    else -> Text(g.cls.suffix(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (vm.ply > 0) moveLabel(line, vm.ply - 1) else stringResource(R.string.start_position),
                        fontFamily = Mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    g?.let { Text(it.cls.label(), color = it.cls.color(), style = MaterialTheme.typography.labelLarge) }
                }
                if (notes.isNotEmpty()) {
                    Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            e?.let {
                // The score is from the bottom side's view, on a chip of that side's color.
                val whiteView = !vm.flipped
                Surface(
                    color = if (whiteView) Color.White else Color(0xFF14161B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        it.eval.text(whiteView),
                        Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        color = if (whiteView) Color(0xFF14161B) else Color.White,
                        fontFamily = Mono,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Move chips in a row that follows the selected move, then nav buttons and Analyze. */
@Composable
private fun Footer(vm: GameViewModel, onAnalyze: () -> Unit, onReanalyze: (() -> Unit)?) {
    val line = vm.line ?: return
    val list = rememberLazyListState()
    LaunchedEffect(vm.ply) { list.animateScrollToItem((vm.ply - 3).coerceAtLeast(0)) }
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        state = list,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 16.dp),
    ) {
        items(line.plies.size) { i ->
            val selected = vm.ply == i + 1
            val cls = vm.grade(i)?.cls
            val label = moveLabel(line, i)
            val desc = cls?.let { stringResource(R.string.move_with_class, label, it.label()) } ?: label
            Surface(
                onClick = { vm.goTo(i + 1) },
                modifier = Modifier.heightIn(min = 36.dp).semantics { contentDescription = desc },
                shape = RoundedCornerShape(10.dp),
                // The user's own moves get an outline.
                border = vm.variation?.takeIf { i >= it.branch }?.let { BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) },
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cls != null) Box(Modifier.size(7.dp).clip(CircleShape).background(cls.color()))
                    Text(label + (cls?.suffix() ?: ""), fontFamily = Mono, fontSize = 13.sp)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val best = vm.evals.getOrNull(vm.ply)?.best
        if (vm.variation != null) {
            TextButton(onClick = vm::leaveVariation, modifier = Modifier.height(52.dp)) { Text(stringResource(R.string.back_to_game)) }
            Box(Modifier.weight(1f))
        } else if (vm.analysis is AnalysisState.Done && onReanalyze == null) {
            Box(Modifier.weight(1f))
        } else {
            AnalyzeButton(vm, onAnalyze, onReanalyze, Modifier.weight(1f).height(52.dp))
        }
        SquareIconButton(R.drawable.ic_star, stringResource(R.string.follow_best), { best?.let(vm::play) }, size = 52.dp, enabled = best != null)
        SquareIconButton(R.drawable.ic_back, stringResource(R.string.nav_prev), { vm.goTo(vm.ply - 1) }, size = 52.dp)
        SquareIconButton(R.drawable.ic_next, stringResource(R.string.nav_next), { vm.goTo(vm.ply + 1) }, size = 52.dp)
    }
}

@Composable
fun ErrorText(error: LoadError) {
    val text = when (error) {
        is LoadError.Parse -> stringResource(R.string.error_pgn, error.e.line, error.e.column, error.e.reason)
        LoadError.TooLarge -> stringResource(R.string.error_too_large, LibraryViewModel.MAX_FILE_BYTES / 1024 / 1024)
        LoadError.Unreadable -> stringResource(R.string.error_unreadable)
    }
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PasteDialog(onDismiss: () -> Unit, onLoad: (String) -> Unit) {
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
