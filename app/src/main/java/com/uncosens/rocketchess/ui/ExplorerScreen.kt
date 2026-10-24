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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.analysis.EngineOptionsStore
import com.uncosens.rocketchess.engine.StockfishSession
import com.uncosens.rocketchess.opening.OpeningData

/** Walk the Lichess rapid opening statistics from the start position. */
@Composable
fun ExplorerScreen(vm: ExplorerViewModel, onAnalyze: (pgn: String) -> Unit, onPlay: (fen: String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val exe = remember { StockfishSession.binary(context) }
    // Leaving frees the engine behind the best move arrow.
    val leave = { vm.stop(); onBack() }
    BackHandler(onBack = leave)
    LaunchedEffect(vm.fen, vm.showBest) { vm.findBest(exe, EngineOptionsStore.load(context), vm.fen) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (maxWidth > maxHeight) {
            val side = maxHeight - 72.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.padding(top = 8.dp)) { ExplorerBoard(vm, Modifier.size(side)) }
                Column(Modifier.weight(1f)) {
                    Header(vm, leave)
                    Stats(vm, Modifier.weight(1f))
                    Actions(vm, onAnalyze, onPlay)
                }
            }
        } else {
            val side = minOf(maxWidth, LocalConfiguration.current.screenWidthDp.dp * 0.9f)
            Column {
                Header(vm, leave)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { ExplorerBoard(vm, Modifier.size(side)) }
                Stats(vm, Modifier.weight(1f))
                Actions(vm, onAnalyze, onPlay)
            }
        }
    }
}

@Composable
private fun Header(vm: ExplorerViewModel, onBack: () -> Unit) {
    TopBar(stringResource(R.string.explorer_title), onBack, stringResource(R.string.explorer_subtitle)) {
        // Up here so the bottom row keeps room for the Analyze and Play labels on a phone.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareIconButton(
                R.drawable.ic_star, stringResource(R.string.show_best), vm::toggleBest,
                container = if (vm.showBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
            )
            SquareIconButton(R.drawable.ic_flip, stringResource(R.string.flip_board), { vm.flipped = !vm.flipped })
        }
    }
}

@Composable
private fun ExplorerBoard(vm: ExplorerViewModel, modifier: Modifier) {
    val position = vm.line.positions[vm.ply]
    val last = vm.line.plies.getOrNull(vm.ply - 1)
    val input = rememberMoveInput(position.fen, enabled = true) { vm.play(it) }
    ChessBoard(
        fen = position.fen,
        lastMove = last?.let { it.from to it.to },
        check = position.check,
        description = if (last == null) stringResource(R.string.board_start) else stringResource(R.string.board_after, last.san),
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        flipped = vm.flipped,
        arrow = vm.hint?.takeIf { vm.showBest && it.first == position.fen }?.second?.let { it.substring(0, 2) to it.substring(2, 4) },
        selected = input.selected,
        targets = input.targets,
        onTap = input::tap,
    )
}

/** Opening name, rating bands and the moves played in the shown position. */
@Composable
private fun Stats(vm: ExplorerViewModel, modifier: Modifier) {
    Column(modifier.padding(top = 8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val book = OpeningData.book
        if (book == null) {
            Text(stringResource(R.string.explorer_loading), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val names = OpeningData.names
        val opening = remember(names, vm.line, vm.ply) { names?.of(vm.line, vm.ply) }
        OpeningTitle(opening, reserve = true)
        BandChips(book.chesscomEdges)
        val fen = vm.fen
        val position = remember(book, fen) { book.lookup(fen) }
        if (position == null) Text(stringResource(R.string.explorer_no_games), style = MaterialTheme.typography.bodyMedium)
        else BookMoves(fen, position, vm::play)
    }
}

/** Analyze or play the shown position, and step through the line. */
@Composable
private fun Actions(vm: ExplorerViewModel, onAnalyze: (String) -> Unit, onPlay: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAnalyze(vm.pgn()) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.analyze), maxLines = 1)
        }
        OutlinedButton(onClick = { onPlay(vm.fen) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.explorer_play), maxLines = 1)
        }
        SquareIconButton(R.drawable.ic_back, stringResource(R.string.nav_prev), { vm.goTo(vm.ply - 1) }, size = 52.dp, enabled = vm.ply > 0)
        SquareIconButton(R.drawable.ic_next, stringResource(R.string.nav_next), { vm.goTo(vm.ply + 1) }, size = 52.dp, enabled = vm.ply < vm.line.plies.size)
    }
}
