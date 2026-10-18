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
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.ui.AnalysisState
import com.uncosens.rocketchess.ui.GameScreen
import com.uncosens.rocketchess.ui.GameViewModel
import com.uncosens.rocketchess.ui.LibraryScreen
import com.uncosens.rocketchess.ui.LicensesScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RocketTheme {
                Surface(Modifier.fillMaxSize()) {
                    // A few screens; a nav library is not worth it yet.
                    var screen by rememberSaveable { mutableStateOf(LIBRARY) }
                    // The screen a game was opened from, so Back returns there.
                    var from by rememberSaveable { mutableStateOf(LIBRARY) }
                    val open = { pgn: String, key: String, owner: String? ->
                        game.load(pgn, key, owner); from = screen; screen = GAME
                    }
                    // A finished analysis stores the game's accuracy for the lists.
                    LaunchedEffect(game.analysis) {
                        val key = game.key
                        if (game.analysis is AnalysisState.Done && key != null) library.saveAccuracy(key, game.accuracy(true), game.accuracy(false))
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
                            SETUP -> SetupScreen(onAnalyze = { open(it, Sources.textKey(it), null) }, onBack = { screen = LIBRARY })
                            // Games against the engine open without being saved; "You" is the owner.
                            PLAY -> PlayScreen(play, onAnalyze = { open(it, Sources.textKey(it), getString(R.string.you)) }, onBack = { screen = LIBRARY })
                            else -> LibraryScreen(
                                library, onOpen = open, onOnline = { screen = ONLINE }, onSetup = { screen = SETUP }, onPlay = { screen = PLAY },
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
    }
}
