package com.uncosens.rocketchess.ui

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.chess.Setup
import com.uncosens.rocketchess.chess.SetupError

/** Place pieces by hand, pick the side to move, then analyze from that position. */
@Composable
fun SetupScreen(onAnalyze: (pgn: String) -> Unit, onPlay: (fen: String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var fen by rememberSaveable { mutableStateOf(GameLine.START_FEN) }
    // The piece letter to place, or ' ' to remove pieces.
    var tool by rememberSaveable { mutableStateOf('P') }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val whiteToMove = fen.split(' ')[1] == "w"
    val error = remember(fen) { Setup.error(fen) }

    val place = { sq: String ->
        val i = (sq[0] - 'a') + (sq[1] - '1') * 8
        val map = pieces(fen).toMutableMap()
        when {
            tool == ' ' || map[i] == tool -> map.remove(i)
            else -> {
                // Only one king per side: placing it moves it.
                if (tool == 'K' || tool == 'k') map.values.removeAll { it == tool }
                map[i] = tool
            }
        }
        fen = Setup.fen(map, whiteToMove)
    }
    val board = @Composable { m: Modifier ->
        ChessBoard(fen, null, null, stringResource(R.string.setup_board), m.clip(RoundedCornerShape(12.dp)), flipped = flipped, onTap = place)
    }
    val controls = @Composable {
        Controls(
            tool = tool,
            onTool = { tool = it },
            whiteToMove = whiteToMove,
            onSide = { fen = Setup.fen(pieces(fen), it) },
            onStart = { fen = GameLine.START_FEN },
            onClear = { fen = Setup.fen(emptyMap(), whiteToMove) },
            error = error,
            onAnalyze = { onAnalyze(Setup.pgn(fen)) },
            onPlay = { onPlay(fen) },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (maxWidth > maxHeight) {
            val side = maxHeight - 16.dp
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                board(Modifier.padding(top = 8.dp).size(side))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopBar(stringResource(R.string.setup_board), onBack) {
                        SquareIconButton(R.drawable.ic_flip, stringResource(R.string.flip_board), { flipped = !flipped })
                    }
                    controls()
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar(stringResource(R.string.setup_board), onBack) {
                    SquareIconButton(R.drawable.ic_flip, stringResource(R.string.flip_board), { flipped = !flipped })
                }
                board(Modifier.fillMaxWidth())
                controls()
            }
        }
    }
}

@Composable
private fun Controls(
    tool: Char,
    onTool: (Char) -> Unit,
    whiteToMove: Boolean,
    onSide: (Boolean) -> Unit,
    onStart: () -> Unit,
    onClear: () -> Unit,
    error: SetupError?,
    onAnalyze: () -> Unit,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val images = remember { PieceSetStore.images(context, PieceSetStore.load(context)) }
    for (row in listOf("KQRBNP", "kqrbnp")) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in row) {
                // Tapping the picked piece again unpicks it, which switches to removing.
                ToolButton(selected = tool == p, description = p.toString(), onClick = { onTool(if (tool == p) ' ' else p) }) {
                    Canvas(Modifier.size(40.dp)) {
                        drawIntoCanvas { images[p]?.renderToCanvas(it.nativeCanvas, RectF(0f, 0f, size.width, size.height)) }
                    }
                }
            }
        }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(true to R.string.white_to_move, false to R.string.black_to_move).forEachIndexed { i, (white, label) ->
            SegmentedButton(
                selected = whiteToMove == white,
                onClick = { onSide(white) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(stringResource(label)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onStart) { Text(stringResource(R.string.start_position)) }
        TextButton(onClick = onClear) { Text(stringResource(R.string.clear_board)) }
    }
    error?.let {
        val text = when (it) {
            SetupError.KINGS -> R.string.setup_err_kings
            SetupError.PAWNS -> R.string.setup_err_pawns
            SetupError.TOO_MANY_PAWNS -> R.string.setup_err_too_many_pawns
            SetupError.TOO_MANY_PIECES -> R.string.setup_err_too_many_pieces
            SetupError.CHECK -> R.string.setup_err_check
        }
        Text(stringResource(text), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAnalyze, enabled = error == null, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.analyze))
        }
        // Plays the engine from this position; lime like the other Play buttons.
        Button(
            onClick = onPlay,
            enabled = error == null,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LocalAccents.current.play.color, contentColor = LocalAccents.current.play.on),
        ) {
            Text(stringResource(R.string.play_engine))
        }
    }
}

/** A square palette button with an outline when selected. Light-square color so both piece colors show. */
@Composable
private fun ToolButton(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = LocalBoardColors.current.light,
        contentColor = Color(0xFF14161B),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.size(52.dp).semantics { contentDescription = description; this.selected = selected },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
