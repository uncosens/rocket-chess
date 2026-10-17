package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpen: (String) -> Unit, onOnline: () -> Unit) {
    val games by vm.games.collectAsState()
    var pasting by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) } // -1 = delete all
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFile(it, onOpen) }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pasting = true }) { Text(stringResource(R.string.paste_pgn)) }
            FilledTonalButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.open_file)) }
            FilledTonalButton(onClick = onOnline) { Text(stringResource(R.string.import_online)) }
        }
        vm.error?.let { ErrorText(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.saved_games, games.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (games.isNotEmpty()) TextButton(onClick = { confirmDelete = -1 }) { Text(stringResource(R.string.delete_all)) }
        }
        if (games.isEmpty()) Text(stringResource(R.string.no_saved_games), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(games, key = { it.id }) { g ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.open(g.id, onOpen) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.players, g.white, g.black) + "  " + g.result)
                        Text("${sourceLabel(g.source)} · ${g.date}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { confirmDelete = g.id }) { Text(stringResource(R.string.delete)) }
                }
                HorizontalDivider()
            }
        }
        Text(stringResource(R.string.storage_note), style = MaterialTheme.typography.bodySmall)
    }

    if (pasting) PasteDialog(onDismiss = { pasting = false }, onLoad = { pasting = false; vm.importText(it, Sources.PASTE, onOpen) })
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

/** Search a Lichess or Chess.com user and pick a game. */
@Composable
fun OnlineScreen(vm: LibraryViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var source by rememberSaveable { mutableStateOf(Sources.CHESS_COM) }
    var name by rememberSaveable { mutableStateOf("") }
    BackHandler { vm.clearOnline(); onBack() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { vm.clearOnline(); onBack() }) { Text(stringResource(R.string.back_to_games)) }
        SingleChoiceSegmentedButtonRow {
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
                modifier = Modifier.weight(1f),
            )
            Button(onClick = search, enabled = name.isNotBlank() && vm.online != OnlineState.Loading) { Text(stringResource(R.string.search)) }
        }
        Text(stringResource(R.string.online_note), style = MaterialTheme.typography.bodySmall)
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
                            )
                        }
                        HorizontalDivider()
                    }
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
