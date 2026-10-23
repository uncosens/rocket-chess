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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.EngineOptionsStore
import com.uncosens.rocketchess.chess.Finish
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.engine.StockfishSession
import kotlin.math.roundToInt

/** Play Stockfish from the start position, then open the game for analysis. */
@Composable
fun PlayScreen(vm: PlayViewModel, onAnalyze: (pgn: String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val exe = remember { StockfishSession.binary(context) }
    // Leaving frees the engine; the game stays and the engine picks up again on return.
    val leave = { vm.close(); onBack() }
    BackHandler(onBack = leave)
    LaunchedEffect(vm.line, vm.thinking, vm.result, vm.hinting) { vm.engineMove() }

    val line = vm.line
    // The live position, or an earlier one while looking back.
    val shown = vm.viewPly ?: line?.plies?.size ?: 0
    val live = vm.viewPly == null
    val fen = vm.shownFen
    val position = line?.positions?.getOrNull(shown)
    val last = line?.plies?.getOrNull(shown - 1)
    LaunchedEffect(fen, vm.showBest, vm.thinking, vm.hinting) { vm.findBest(fen) }
    val best = vm.hint?.takeIf { vm.showBest && it.first == fen }?.second
    val input = rememberMoveInput(fen, enabled = line != null && live && !vm.engineTurn && vm.result == null) { vm.play(it) }
    val (whiteEdge, blackEdge) = remember(fen) { materialEdge(fen) }
    val you = stringResource(R.string.you)
    val engine = stringResource(R.string.engine_name, vm.elo)
    val board = @Composable { m: Modifier ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The engine on top, the user below, each with the material they are up.
            if (vm.userWhite) PlayerRow(engine, false, blackEdge) else PlayerRow(engine, true, whiteEdge)
            ChessBoard(
                fen = fen,
                lastMove = last?.let { it.from to it.to },
                check = position?.check,
                description = stringResource(R.string.play_engine),
                modifier = m.clip(RoundedCornerShape(12.dp)),
                flipped = !vm.userWhite,
                arrow = best?.let { it.substring(0, 2) to it.substring(2, 4) },
                ending = when {
                    position?.mate == true -> Ending(whiteLost = fen.split(' ')[1] == "w", Finish.MATE)
                    !live -> null
                    vm.resigned -> Ending(whiteLost = vm.userWhite, Finish.RESIGNED)
                    vm.result == "1/2-1/2" -> Ending(whiteLost = false, Finish.DRAW)
                    else -> null
                },
                selected = input.selected,
                targets = input.targets,
                onTap = input::tap,
            )
            if (vm.userWhite) PlayerRow(you, true, whiteEdge) else PlayerRow(you, false, blackEdge)
        }
    }
    val controls = @Composable {
        if (line == null) StartPanel(vm.elo) { white, elo -> vm.start(exe, EngineOptionsStore.load(context), white, elo) }
        else GamePanel(vm, onAnalyze)
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (maxWidth > maxHeight) {
            // Room for the two player rows above and below the board.
            val side = maxHeight - 76.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                board(Modifier.padding(top = 8.dp).size(side))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopBar(stringResource(R.string.play_engine), leave)
                    controls()
                }
            }
        } else {
            // Same board size rule as the analysis screen: 90% of the screen width.
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(stringResource(R.string.play_engine), leave)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { board(Modifier.size(side)) }
                controls()
            }
        }
    }
}

/** Side and engine strength, then Start. */
@Composable
private fun StartPanel(startElo: Int, onStart: (white: Boolean, elo: Int) -> Unit) {
    // 0 = White, 1 = Black, 2 = random.
    var side by rememberSaveable { mutableIntStateOf(0) }
    var elo by rememberSaveable { mutableIntStateOf(startElo) }
    Text(stringResource(R.string.play_side), style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(R.string.side_white, R.string.side_black, R.string.side_random).forEachIndexed { i, label ->
            SegmentedButton(selected = side == i, onClick = { side = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) {
                Text(stringResource(label))
            }
        }
    }
    val range = PlayViewModel.ELO_RANGE
    Text(stringResource(R.string.play_strength, elo), style = MaterialTheme.typography.titleMedium)
    Slider(
        value = elo.toFloat(),
        onValueChange = { elo = (it / 100).roundToInt() * 100 },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / 100 - 1,
    )
    Button(
        onClick = { onStart(if (side == 2) Math.random() < 0.5 else side == 0, elo) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(stringResource(R.string.play_start)) }
}

/** Whose turn or the result, then Resign, or New game and Analyze once it is over. */
@Composable
private fun GamePanel(vm: PlayViewModel, onAnalyze: (String) -> Unit) {
    val you = stringResource(R.string.you)
    val engine = stringResource(R.string.engine_name, vm.elo)
    val r = vm.result
    val status = when {
        vm.error != null -> stringResource(R.string.analysis_failed, vm.error!!)
        r == "1/2-1/2" -> stringResource(R.string.play_draw)
        r != null -> {
            val won = (r == "1-0") == vm.userWhite
            stringResource(
                when {
                    vm.resigned -> R.string.play_lost_resign
                    won -> R.string.play_won_mate
                    else -> R.string.play_lost_mate
                },
            )
        }
        vm.thinking -> stringResource(R.string.play_thinking)
        else -> stringResource(R.string.play_your_move)
    }
    Text(stringResource(R.string.play_vs, engine, stringResource(if (vm.userWhite) R.string.side_white else R.string.side_black)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(status, style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r == null) {
            OutlinedButton(onClick = vm::undo, enabled = vm.canUndo, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.play_undo))
            }
            OutlinedButton(onClick = vm::resign, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.play_resign))
            }
        } else {
            OutlinedButton(onClick = vm::reset, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.play_new))
            }
            Button(onClick = { onAnalyze(vm.pgn(you, engine)) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.analyze))
            }
        }
    }
    // Best move arrow for the shown position, and looking back over the game.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        SquareIconButton(
            R.drawable.ic_star, stringResource(R.string.show_best), vm::toggleBest, size = 52.dp,
            container = if (vm.showBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        )
        SquareIconButton(R.drawable.ic_back, stringResource(R.string.nav_prev), vm::back, size = 52.dp, enabled = (vm.viewPly ?: vm.line?.plies?.size ?: 0) > 0)
        SquareIconButton(R.drawable.ic_next, stringResource(R.string.nav_next), vm::forward, size = 52.dp, enabled = vm.viewPly != null)
    }
}
