package com.uncosens.rocketchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.BuildConfig
import com.uncosens.rocketchess.R

private const val APP_SOURCE = "https://github.com/uncosens/rocket-chess"
private const val STOCKFISH_SOURCE = "https://github.com/official-stockfish/Stockfish"

/** App license, Stockfish source link and build facts, third-party notices and full license texts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var file by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (file != null) file = null else onBack() }
    val uri = LocalUriHandler.current
    val commit = BuildConfig.GIT_COMMIT.removeSuffix("-dirty")

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { if (file != null) file = null else onBack() }) { Text(stringResource(R.string.back)) }
        val shown = file
        if (shown != null) {
            LicenseText(shown)
            return@Column
        }
        Text(stringResource(R.string.licenses_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.licenses_app, BuildConfig.VERSION_NAME))
        TextButton(onClick = { uri.openUri("$APP_SOURCE/tree/$commit") }) { Text(stringResource(R.string.licenses_app_source)) }
        Text(stringResource(R.string.licenses_stockfish, BuildConfig.STOCKFISH_TAG))
        TextButton(onClick = { uri.openUri("$STOCKFISH_SOURCE/tree/${BuildConfig.STOCKFISH_COMMIT}") }) {
            Text(stringResource(R.string.licenses_stockfish_source))
        }
        Text(stringResource(R.string.licenses_libraries))
        SelectionContainer {
            Text(
                stringResource(R.string.licenses_build, BuildConfig.GIT_COMMIT, BuildConfig.STOCKFISH_COMMIT, BuildConfig.ENGINE_SHA256),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { file = "GPL-3.0.txt" }) { Text("GPL-3.0") }
            FilledTonalButton(onClick = { file = "Apache-2.0.txt" }) { Text("Apache-2.0") }
            FilledTonalButton(onClick = { file = "third_party.txt" }) { Text(stringResource(R.string.licenses_notices)) }
        }
    }
}

@Composable
private fun LicenseText(name: String) {
    val context = LocalContext.current
    val text = remember(name) { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }
    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
}
