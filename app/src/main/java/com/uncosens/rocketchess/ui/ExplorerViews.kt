package com.uncosens.rocketchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.opening.BookPosition
import com.uncosens.rocketchess.opening.Opening
import com.uncosens.rocketchess.opening.OpeningData
import com.uncosens.rocketchess.opening.Tally
import java.text.NumberFormat

/** "B90 Sicilian Defense: Najdorf Variation", or nothing. With [reserve], two lines are kept even with no name, so nothing below moves. */
@Composable
fun OpeningTitle(opening: Opening?, modifier: Modifier = Modifier, reserve: Boolean = false) {
    if (opening == null && !reserve) return
    Text(
        opening?.let { "${it.eco} ${it.name}" } ?: "",
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        minLines = if (reserve) 2 else 1,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** All ratings, then one chip per band of the book, labeled with Chess.com rapid ratings. Saves the pick. */
@Composable
fun BandChips(edges: List<Int>) {
    val context = LocalContext.current
    val band = OpeningData.band
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = band == null, onClick = { OpeningData.pickBand(context, null) }, label = { Text(stringResource(R.string.band_all)) })
        for (i in 0..edges.size) {
            val label = when (i) {
                0 -> stringResource(R.string.band_under, edges[0])
                edges.size -> stringResource(R.string.band_over, edges.last())
                else -> stringResource(R.string.band_range, edges[i - 1], edges[i])
            }
            FilterChip(selected = band == i, onClick = { OpeningData.pickBand(context, i) }, label = { Text(label) })
        }
    }
}

/** Moves played in [position] for the picked band, most played first. Tap one to play it. */
@Composable
fun BookMoves(fen: String, position: BookPosition, onMove: (String) -> Unit, limit: Int = Int.MAX_VALUE) {
    val band = OpeningData.band
    val total = position.tally(band).games
    val moves = position.moves(band).take(limit)
    if (moves.isEmpty()) {
        Text(stringResource(R.string.explorer_no_games), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val format = NumberFormat.getIntegerInstance()
    Column {
        for (m in moves) {
            val t = m.tally(band)
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).clickable { onMove(m.uci) }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(GameLine.san(fen, m.uci), Modifier.width(64.dp), fontFamily = Mono, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${100 * t.games / maxOf(total, 1)}%",
                    Modifier.width(40.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    format.format(t.games),
                    Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                ResultBar(t, Modifier.weight(1f))
            }
        }
    }
}

/** White wins, draws and Black wins as one bar, with the percent on parts wide enough for it. */
@Composable
fun ResultBar(t: Tally, modifier: Modifier = Modifier) {
    val games = maxOf(t.games, 1)
    Row(modifier.height(18.dp).clip(RoundedCornerShape(4.dp))) {
        for ((n, colors) in listOf(t.white to (Color(0xFFF2F2F2) to Color(0xFF14161B)), t.draws to (Color(0xFF8C96AB) to Color.White), t.black to (Color(0xFF2B2E33) to Color.White))) {
            if (n == 0) continue
            Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colors.first), contentAlignment = Alignment.Center) {
                val pct = 100 * n / games
                if (pct >= 15) Text("$pct", color = colors.second, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
            }
        }
    }
}

/** Opening name and the most played book moves for the analysis screen. Nothing outside the book. */
@Composable
fun ExplorerCard(vm: GameViewModel, line: GameLine) {
    val book = OpeningData.book ?: return
    val fen = line.positions[vm.ply].fen
    val position = remember(book, fen) { book.lookup(fen) }
    if (position == null && vm.opening == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OpeningTitle(vm.opening)
            if (position != null) {
                Text(stringResource(R.string.explorer_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BookMoves(fen, position, vm::play, limit = 3)
            }
        }
    }
}
