package com.uncosens.rocketchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uncosens.rocketchess.engine.StockfishSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val exe = StockfishSession.binary(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var result by remember { mutableStateOf<String?>(null) }
                    // M1 smoke check: one short search from the start position.
                    LaunchedEffect(Unit) {
                        result = withContext(Dispatchers.IO) {
                            runCatching {
                                StockfishSession(exe).use { sf ->
                                    sf.send("uci")
                                    val id = sf.readUntil(5_000) { it == "uciok" }.first { it.startsWith("id name") }
                                    sf.send("isready")
                                    sf.readUntil(5_000) { it == "readyok" }
                                    sf.send("position startpos")
                                    sf.send("go depth 12")
                                    "$id\n" + sf.readUntil(60_000) { it.startsWith("bestmove") }.last()
                                }
                            }.getOrElse { "error: ${it.message}" }
                        }
                    }
                    Text(result ?: stringResource(R.string.engine_running), Modifier.padding(24.dp))
                }
            }
        }
    }
}
