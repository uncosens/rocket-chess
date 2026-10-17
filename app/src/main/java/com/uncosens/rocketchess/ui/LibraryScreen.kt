package com.uncosens.rocketchess.ui

import android.content.ClipboardManager
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.remote.FetchError
import java.text.DateFormat
import java.util.Date

/** Saved games and the ways to add one. */
@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpen: (String) -> Unit, onImport: (String) -> Unit, onSettings: () -> Unit, onLicenses: () -> Unit) {
    val games by vm.games.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) } // -1 = delete all
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
        Button(onClick = { onImport(Sources.PASTE) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(painterResource(R.drawable.ic_paste), null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.paste_pgn), style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ImportButton(R.drawable.ic_file, stringResource(R.string.open_file), Modifier.weight(1f)) { openFile.launch(arrayOf("*/*")) }
            ImportButton(R.drawable.ic_globe, stringResource(R.string.import_online), Modifier.weight(1f)) { onImport(Sources.CHESS_COM) }
        }
        vm.error?.let { ErrorText(it) }

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.saved_games, games.size), Modifier.weight(1f))
            if (games.isNotEmpty()) TextButton(onClick = { confirmDelete = -1 }) { Text(stringResource(R.string.delete_all)) }
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
                        Text(stringResource(R.string.players, g.white, g.black), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${sourceLabel(g.source)} · ${g.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(g.result, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { confirmDelete = g.id }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.storage_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onLicenses) { Text(stringResource(R.string.about_licenses)) }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(if (id == -1L) R.string.confirm_delete_all else R.string.confirm_delete)) },
            confirmButton = {
                TextButton(onClick = { if (id == -1L) vm.deleteAll() else vm.delete(id); confirmDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun sourceLabel(source: String) = stringResource(
    when (source) {
        Sources.CHESS_COM -> R.string.source_chesscom
        Sources.LICHESS -> R.string.source_lichess
        Sources.FILE -> R.string.source_file
        else -> R.string.source_paste
    },
)

private fun sourceIcon(source: String) = when (source) {
    Sources.CHESS_COM, Sources.LICHESS -> R.drawable.ic_globe
    Sources.FILE -> R.drawable.ic_file
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

/** One page to import a game: PGN text or file, Chess.com or Lichess search. [startTab] is a [Sources] value. */
@Composable
fun ImportScreen(vm: LibraryViewModel, startTab: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // Not saveable: a big PGN would overflow the saved-state bundle.
    var pgn by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFile(it, onOpen) }
    }
    val back = { vm.clearOnline(); onBack() }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar(stringResource(R.string.import_game), onBack = back)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val tabs = listOf(Sources.PASTE, Sources.CHESS_COM, Sources.LICHESS)
            tabs.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = tab == s,
                    onClick = { tab = s; vm.clearOnline() },
                    shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                ) { Text(if (s == Sources.PASTE) stringResource(R.string.tab_pgn) else sourceLabel(s)) }
            }
        }
        if (tab == Sources.PASTE) {
            OutlinedTextField(
                value = pgn,
                onValueChange = { pgn = it },
                label = { Text(stringResource(R.string.pgn_hint)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImportButton(R.drawable.ic_paste, stringResource(R.string.paste), Modifier.weight(1f)) {
                    context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.let { pgn = it.toString() }
                }
                ImportButton(R.drawable.ic_file, stringResource(R.string.open_file), Modifier.weight(1f)) { openFile.launch(arrayOf("*/*")) }
            }
            Text(stringResource(R.string.import_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.error?.let { ErrorText(it) }
            Button(
                onClick = { vm.importText(pgn, Sources.PASTE, onOpen) },
                enabled = pgn.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(stringResource(R.string.load_game), style = MaterialTheme.typography.titleMedium) }
        } else {
            OnlineSearch(vm, tab, name, { name = it }, onOpen)
        }
    }
}

/** Search a Lichess or Chess.com user and pick a game. */
@Composable
private fun ColumnScope.OnlineSearch(vm: LibraryViewModel, source: String, name: String, onName: (String) -> Unit, onOpen: (String) -> Unit) {
    val search = { if (name.isNotBlank()) vm.fetch(source, name) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.user_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = search, enabled = name.isNotBlank() && vm.online != OnlineState.Loading, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.search))
        }
    }
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
            if (s.games.isEmpty()) Text(stringResource(R.string.no_public_games))
            LazyColumn(Modifier.weight(1f)) {
                items(s.games, key = { it.key }) { g ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.importRemote(g, onOpen) }.padding(vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.players, g.white, g.black) + "  " + (com.uncosens.rocketchess.remote.tag(g.pgn, "Result") ?: ""))
                        Text(
                            listOfNotNull(g.speed, DateFormat.getDateInstance().format(Date(g.endedAt))).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

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
