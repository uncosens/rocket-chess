package com.uncosens.rocketchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uncosens.rocketchess.chess.GameLine
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.opening.OpeningData
import com.uncosens.rocketchess.ui.AnalysisState
import com.uncosens.rocketchess.ui.ExplorerScreen
import com.uncosens.rocketchess.ui.ExplorerViewModel
import com.uncosens.rocketchess.ui.GameScreen
import com.uncosens.rocketchess.ui.GameViewModel
import com.uncosens.rocketchess.ui.LibraryScreen
import com.uncosens.rocketchess.ui.LicensesScreen
import com.uncosens.rocketchess.ui.OpenGame
import com.uncosens.rocketchess.ui.PlayScreen
import com.uncosens.rocketchess.ui.PlayViewModel
import com.uncosens.rocketchess.ui.LibraryViewModel
import com.uncosens.rocketchess.ui.OnlineScreen
import com.uncosens.rocketchess.ui.RocketTheme
import com.uncosens.rocketchess.ui.SettingsScreen
import com.uncosens.rocketchess.ui.SetupScreen

class MainActivity : ComponentActivity() {
    private val game: GameViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()
    private val play: PlayViewModel by viewModels()
    private val explorer: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RocketTheme {
                Surface(Modifier.fillMaxSize()) {
                    LaunchedEffect(Unit) { OpeningData.load(applicationContext) }
                    // A few screens; a nav library is not worth it yet.
                    var screen by rememberSaveable { mutableStateOf(LIBRARY) }
                    // The screen a game was opened from, so Back returns there.
                    var from by rememberSaveable { mutableStateOf(LIBRARY) }
                    // The screen the engine game was opened from, so Back returns there.
                    var playFrom by rememberSaveable { mutableStateOf(LIBRARY) }
                    val open: OpenGame = { pgn, key, owner, analysis ->
                        game.load(pgn, key, owner, analysis); from = screen; screen = GAME
                    }
                    // A finished or stopped run saves its results; a finished one also stores the accuracy for the lists.
                    // Keyed on the game too: two games done with the same settings have equal states.
                    LaunchedEffect(game.key, game.analysis) {
                        val key = game.key ?: return@LaunchedEffect
                        val a = game.analysis
                        if (a is AnalysisState.Done || a is AnalysisState.Stopped) game.takeAnalysisToSave()?.let { library.saveAnalysis(key, it) }
                        if (a is AnalysisState.Done) library.saveAccuracy(key, game.accuracy(true), game.accuracy(false))
                    }
                    // Keeps each screen's own state, like the search results' scroll, while another is shown.
                    val saved = rememberSaveableStateHolder()
                    saved.SaveableStateProvider(screen) {
                        when (screen) {
                            GAME -> GameScreen(game, onBack = { game.stopAnalysis(); screen = from })
                            ONLINE -> OnlineScreen(library, onOpen = open, onBack = { screen = LIBRARY })
                            LICENSES -> LicensesScreen(onBack = { screen = SETTINGS })
                            SETTINGS -> SettingsScreen(onBack = { screen = LIBRARY }, onLicenses = { screen = LICENSES })
                            // Set-up positions open without being saved.
                            SETUP -> SetupScreen(
                                onAnalyze = { open(it, Sources.textKey(it), null, null) },
                                // A new game from the set-up position replaces any game in progress.
                                onPlay = { play.prepare(it); playFrom = SETUP; screen = PLAY },
                                onBack = { screen = LIBRARY },
                            )
                            // Like set-up positions, explorer lines open without being saved.
                            EXPLORER -> ExplorerScreen(
                                explorer,
                                onAnalyze = { open(it, Sources.textKey(it), null, null) },
                                onPlay = { play.prepare(it); playFrom = EXPLORER; screen = PLAY },
                                onBack = { screen = LIBRARY },
                            )
                            // Games against the engine are saved once analyzed; "You" is the owner.
                            PLAY -> PlayScreen(play, onAnalyze = { library.importText(it, Sources.ENGINE, open, getString(R.string.you)) }, onBack = { screen = playFrom })
                            else -> LibraryScreen(
                                library, onOpen = open, onOnline = { screen = ONLINE }, onSetup = { screen = SETUP }, onPlay = {
                                    // A game in progress carries on; otherwise the next one starts normally.
                                    if (play.line == null) play.prepare(GameLine.START_FEN)
                                    playFrom = LIBRARY; screen = PLAY
                                },
                                onExplorer = { screen = EXPLORER },
                                onSettings = { screen = SETTINGS },
                            )
                        }
                    }
                }
            }
        }
    }

    // v1 does not analyze in the background: stop and free the engine.
    override fun onStop() {
        super.onStop()
        game.stopAnalysis()
    }

    private companion object {
        const val LIBRARY = "library"
        const val ONLINE = "online"
        const val GAME = "game"
        const val LICENSES = "licenses"
        const val SETTINGS = "settings"
        const val SETUP = "setup"
        const val PLAY = "play"
        const val EXPLORER = "explorer"
    }
}
