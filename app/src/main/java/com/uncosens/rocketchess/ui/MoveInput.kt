package com.uncosens.rocketchess.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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

/** Pick the piece a pawn becomes. [moves] are the same move with each promotion letter. */
@Composable
private fun PromotionDialog(moves: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val names = mapOf('q' to R.string.piece_queen, 'r' to R.string.piece_rook, 'b' to R.string.piece_bishop, 'n' to R.string.piece_knight)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.promote_to)) },
        text = {
            Column {
                for (m in moves.sortedBy { "qrbn".indexOf(it.last()) }) {
                    TextButton(onClick = { onPick(m) }) { Text(stringResource(names.getValue(m.last()))) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
