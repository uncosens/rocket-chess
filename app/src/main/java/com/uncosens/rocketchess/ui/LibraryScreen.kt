package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.R
import com.uncosens.rocketchess.data.KnownUsers
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.data.userSide
import com.uncosens.rocketchess.remote.FetchError
import java.text.DateFormat
import java.util.Date

/** Saved games and the ways to add one. */
@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpen: OpenGame, onOnline: () -> Unit, onSetup: () -> Unit, onPlay: () -> Unit, onSettings: () -> Unit) {
    val games by vm.games.collectAsState()
    var pasting by rememberSaveable { mutableStateOf(false) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFile(it, onOpen) }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RocketMark(34.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            SquareIconButton(R.drawable.ic_settings, stringResource(R.string.engine_settings), onSettings)
        }

        SectionLabel(stringResource(R.string.new_analysis), Modifier.padding(top = 12.dp))
        Button(onClick = onOnline, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(painterResource(R.drawable.ic_globe), null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.import_online), style = MaterialTheme.typography.titleMedium)
        }
        // Two per row so the labels fit on a phone.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ImportButton(R.drawable.ic_paste, stringResource(R.string.paste_pgn), Modifier.weight(1f)) { pasting = true }
            ImportButton(R.drawable.ic_file, stringResource(R.string.open_file), Modifier.weight(1f)) { openFile.launch(arrayOf("*/*")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ImportButton(R.drawable.ic_board, stringResource(R.string.setup_board), Modifier.weight(1f), onSetup)
            ImportButton(R.drawable.ic_engine, stringResource(R.string.play_engine), Modifier.weight(1f), onPlay)
        }
        vm.error?.let { ErrorText(it) }

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.saved_games, games.size), Modifier.weight(1f))
            if (games.isNotEmpty()) TextButton(onClick = vm::deleteAll) { Text(stringResource(R.string.delete_all)) }
        }
        if (games.isEmpty()) Text(stringResource(R.string.no_saved_games), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(games, key = { it.id }) { g ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.open(g.id, onOpen) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(sourceIcon(g.source)), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.players_lines, g.white, g.black), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Weighted, so the accuracy is measured first and keeps its full width.
                            Text(
                                "${sourceLabel(g.source)} · ${g.date}",
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AccuracyText(g.white, g.black, g.whiteAccuracy, g.blackAccuracy, g.owner)
                        }
                    }
                    val outcome = outcomeColor(g.white, g.black, g.result, g.owner)
                    Surface(color = outcome ?: MaterialTheme.colorScheme.surfaceContainer, contentColor = if (outcome != null) Color.White else MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(8.dp)) {
                        Text(g.result, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.delete(g.id) }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (pasting) PasteDialog(onDismiss = { pasting = false }, onLoad = { pasting = false; vm.importText(it, Sources.PASTE, onOpen) })
}

@Composable
private fun sourceLabel(source: String) = stringResource(
    when (source) {
        Sources.CHESS_COM -> R.string.source_chesscom
        Sources.LICHESS -> R.string.source_lichess
        Sources.FILE -> R.string.source_file
        Sources.SETUP -> R.string.source_setup
        Sources.ENGINE -> R.string.source_engine
        else -> R.string.source_paste
    },
)

/** Green when [owner] won the game, red when they lost; null for a draw or when they did not play it. */
private fun outcomeColor(white: String, black: String, result: String, owner: String?): Color? {
    val side = userSide(white, black, owner) ?: return null
    return when (result) {
        "1-0" -> if (side) WIN else LOSS
        "0-1" -> if (side) LOSS else WIN
        else -> null
    }
}

private val WIN = Color(0xFF2EA043)
private val LOSS = Color(0xFFD9363E)

/** The searched user's accuracy in an analyzed game; nothing when no searched user played it. */
@Composable
private fun AccuracyText(white: String, black: String, whiteAcc: Double?, blackAcc: Double?, owner: String?) {
    val text = when (userSide(white, black, owner)) {
        true -> whiteAcc
        false -> blackAcc
        null -> null
    }?.let { stringResource(R.string.accuracy_mine, it) } ?: return
    Text(text, style = MaterialTheme.typography.labelMedium, fontFamily = Mono, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
}

private fun sourceIcon(source: String) = when (source) {
    Sources.CHESS_COM, Sources.LICHESS -> R.drawable.ic_globe
    Sources.FILE -> R.drawable.ic_file
    Sources.SETUP -> R.drawable.ic_board
    Sources.ENGINE -> R.drawable.ic_engine
    else -> R.drawable.ic_paste
}

@Composable
private fun ImportButton(icon: Int, text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Search a Lichess or Chess.com user and pick a game. */
@Composable
fun OnlineScreen(vm: LibraryViewModel, onOpen: OpenGame, onBack: () -> Unit) {
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf(Sources.CHESS_COM) }
    var name by rememberSaveable { mutableStateOf("") }
    // VS shows a second box: only the games played against that name.
    var versus by rememberSaveable { mutableStateOf(false) }
    var opponent by rememberSaveable { mutableStateOf("") }
    // The user's own name on this site, from Settings.
    val myId = KnownUsers.myId(context, source)
    val accuracies by vm.accuracies.collectAsState()
    BackHandler { vm.clearOnline(); onBack() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar(stringResource(R.string.import_online), onBack = { vm.clearOnline(); onBack() })
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(Sources.CHESS_COM, Sources.LICHESS).forEachIndexed { i, s ->
                SegmentedButton(
                    selected = source == s,
                    onClick = { source = s; vm.clearOnline() },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(sourceLabel(s)) }
            }
        }
        val search = { if (name.isNotBlank()) vm.fetch(source, name) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.user_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { versus = !versus; if (!versus) opponent = "" },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = if (versus) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                else ButtonDefaults.filledTonalButtonColors(),
            ) { Text(stringResource(R.string.versus)) }
            // With an empty box, My ID fills in the name; once there is text, Search is back.
            if (name.isBlank() && myId.isNotEmpty()) {
                Button(onClick = { name = myId }, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.my_id))
                }
            } else {
                Button(onClick = search, enabled = name.isNotBlank() && vm.online != OnlineState.Loading, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.search))
                }
            }
        }
        if (versus) OutlinedTextField(
            value = opponent,
            onValueChange = { opponent = it },
            label = { Text(stringResource(R.string.opponent_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.online_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.error?.let { ErrorText(it) }
        when (val s = vm.online) {
            OnlineState.Idle -> {}
            OnlineState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                OutlinedButton(onClick = vm::cancelFetch) { Text(stringResource(R.string.cancel)) }
            }
            is OnlineState.Failed -> Text(fetchErrorText(s.error), color = MaterialTheme.colorScheme.error)
            is OnlineState.Found -> {
                val opp = opponent.trim()
                val shown = if (opp.isEmpty()) s.games else s.games.filter { it.white.equals(opp, true) || it.black.equals(opp, true) }
                if (s.games.isEmpty()) Text(stringResource(R.string.no_public_games))
                else if (shown.isEmpty() && s.done) Text(stringResource(R.string.no_games_vs, opp))
                LazyColumn(Modifier.weight(1f)) {
                    // With a filter the end stays in view, so pages keep loading until all are searched.
                    items(shown, key = { it.key }) { g ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.importRemote(g, onOpen) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                val result = com.uncosens.rocketchess.remote.tag(g.pgn, "Result") ?: ""
                                Text(stringResource(R.string.players_lines, rated(g.white, g.whiteElo), rated(g.black, g.blackElo)) + "  ")
                                Text(result, fontFamily = Mono, color = outcomeColor(g.white, g.black, result, name.trim()) ?: Color.Unspecified)
                                Text(
                                    listOfNotNull(g.speed, DateFormat.getDateInstance().format(Date(g.endedAt))).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            accuracies[g.key]?.let { AccuracyText(g.white, g.black, it.whiteAccuracy, it.blackAccuracy, name.trim()) }
                        }
                        HorizontalDivider()
                    }
                    // Reaching the end of the list loads the next page.
                    if (!s.done) item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            val err = s.moreError
                            if (err != null) {
                                Text(fetchErrorText(err), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                TextButton(onClick = vm::loadMore) { Text(stringResource(R.string.retry)) }
                            } else {
                                LaunchedEffect(s.games.size) { vm.loadMore() }
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "name (1500)", or just the name without a rating. */
private fun rated(name: String, elo: Int?) = if (elo == null) name else "$name ($elo)"

@Composable
private fun fetchErrorText(e: FetchError): String = when (e) {
    FetchError.BadName -> stringResource(R.string.err_bad_name)
    FetchError.NotFound -> stringResource(R.string.err_not_found)
    FetchError.Offline -> stringResource(R.string.err_offline)
    FetchError.Timeout -> stringResource(R.string.err_timeout)
    is FetchError.RateLimited -> stringResource(R.string.err_rate_limited, e.retryAfterSec)
    is FetchError.Server -> stringResource(R.string.err_server, e.code)
    FetchError.BadResponse -> stringResource(R.string.err_bad_response)
}
