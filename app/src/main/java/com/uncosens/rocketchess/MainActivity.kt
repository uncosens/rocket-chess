package com.uncosens.rocketchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uncosens.rocketchess.data.Sources
import com.uncosens.rocketchess.ui.GameScreen
import com.uncosens.rocketchess.ui.GameViewModel
import com.uncosens.rocketchess.ui.ImportScreen
import com.uncosens.rocketchess.ui.LibraryScreen
import com.uncosens.rocketchess.ui.LicensesScreen
import com.uncosens.rocketchess.ui.LibraryViewModel
import com.uncosens.rocketchess.ui.RocketTheme
import com.uncosens.rocketchess.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val game: GameViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RocketTheme {
                Surface(Modifier.fillMaxSize()) {
                    // A few screens; a nav library is not worth it yet.
                    var screen by rememberSaveable { mutableStateOf(LIBRARY) }
                    var importTab by rememberSaveable { mutableStateOf(Sources.PASTE) }
                    val open = { pgn: String -> game.load(pgn); screen = GAME }
                    when (screen) {
                        GAME -> GameScreen(game, onBack = { game.stopAnalysis(); screen = LIBRARY })
                        IMPORT -> ImportScreen(library, importTab, onOpen = open, onBack = { screen = LIBRARY })
                        LICENSES -> LicensesScreen(onBack = { screen = LIBRARY })
                        SETTINGS -> SettingsScreen(onBack = { screen = LIBRARY })
                        else -> LibraryScreen(library, onOpen = open, onImport = { library.clearOnline(); importTab = it; screen = IMPORT }, onSettings = { screen = SETTINGS }, onLicenses = { screen = LICENSES })
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
        const val IMPORT = "import"
        const val GAME = "game"
        const val LICENSES = "licenses"
        const val SETTINGS = "settings"
    }
}
