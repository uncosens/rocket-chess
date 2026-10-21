package com.uncosens.rocketchess.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.chess.GameLine

/** Tap a piece, then a target square, to make a move. Holds the selection for one position. */
class MoveInput(private val legal: List<String>, private val onMove: (String) -> Unit) {
    var selected by mutableStateOf<String?>(null)
        private set
    /** The same move with each promotion piece, waiting for the user's pick. */
    var promotions by mutableStateOf<List<String>>(emptyList())
        private set
    val targets: Set<String>
        get() = selected?.let { s -> legal.filter { it.startsWith(s) }.map { it.substring(2, 4) }.toSet() } ?: emptySet()

    fun tap(sq: String) {
        val moves = selected?.let { s -> legal.filter { it.startsWith(s + sq) } } ?: emptyList()
        when {
            moves.size == 1 -> onMove(moves[0])
            moves.size > 1 -> promotions = moves
            sq != selected && legal.any { it.startsWith(sq) } -> selected = sq
            else -> selected = null
        }
    }

    /** Plays the picked promotion, or cancels with null. */
    fun pick(uci: String?) {
        promotions = emptyList()
        uci?.let(onMove)
    }
}

/** A [MoveInput] for [fen], with no moves when not [enabled]. Shows the promotion picker when needed. */
@Composable
fun rememberMoveInput(fen: String, enabled: Boolean, onMove: (String) -> Unit): MoveInput {
    val move by rememberUpdatedState(onMove)
    val input = remember(fen, enabled) { MoveInput(if (enabled) GameLine.legalMoves(fen) else emptyList()) { move(it) } }
    if (input.promotions.isNotEmpty()) PromotionDialog(input.promotions, onPick = input::pick, onDismiss = { input.pick(null) })
    return input
}

/** Pick the piece a pawn becomes, shown in the saved piece set. [moves] are the same move with each promotion letter. */
@Composable
private fun PromotionDialog(moves: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val names = mapOf('q' to R.string.piece_queen, 'r' to R.string.piece_rook, 'b' to R.string.piece_bishop, 'n' to R.string.piece_knight)
    val context = LocalContext.current
    val images = remember { PieceSetStore.images(context, PieceSetStore.load(context)) }
    // A pawn that reaches rank 8 is White's.
    val white = moves.first()[3] == '8'
    // No buttons: tapping outside or Back cancels.
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in moves.sortedBy { "qrbn".indexOf(it.last()) }) {
                    val piece = if (white) m.last().uppercaseChar() else m.last()
                    val name = stringResource(names.getValue(m.last()))
                    // The board's light square color, so both sides' pieces stand out.
                    Surface(
                        onClick = { onPick(m) },
                        shape = RoundedCornerShape(12.dp),
                        color = LocalBoardColors.current.light,
                        modifier = Modifier.semantics { contentDescription = name },
                    ) {
                        Canvas(Modifier.padding(6.dp).size(52.dp)) {
                            drawIntoCanvas { images[piece]?.renderToCanvas(it.nativeCanvas, RectF(0f, 0f, size.width, size.height)) }
                        }
                    }
                }
            }
        }
    }
}
