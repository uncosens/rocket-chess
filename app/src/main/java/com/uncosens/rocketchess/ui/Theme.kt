package com.uncosens.rocketchess.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.uncosens.rocketchess.R

/** Board square colors. [lastLight]/[lastDark] mark the last move. */
data class BoardColors(val light: Color, val dark: Color, val lastLight: Color, val lastDark: Color)

val LocalBoardColors = staticCompositionLocalOf { NightBoard }

// The Rocket board keeps its "Night Launch" colors in both dark and light mode.
private val NightBoard = BoardColors(Color(0xFFCBD2E1), Color(0xFF59678A), Color(0xFFF2B48F), Color(0xFFC98765))

/** A button color and the text color on it. */
data class Accent(val color: Color, val on: Color)

/** Colors of the main screen's feature buttons; Play also colors the other Play buttons, Notes the Next button. */
data class Accents(val explorer: Accent, val play: Accent, val report: Accent, val notes: Accent)

private val DarkInk = Color(0xFF002324)
// Mauve, Lime, Dusty Sky and Khaki next to the terracotta primary.
private val NightAccents = Accents(
    explorer = Accent(Color(0xFFE5DDDE), DarkInk),
    play = Accent(Color(0xFFEBFACF), DarkInk),
    report = Accent(Color(0xFF9DB9D3), DarkInk),
    notes = Accent(Color(0xFFA1AD95), DarkInk),
)

private val Sapphire = Color(0xFF082567)
private val Glaucous = Color(0xFF6082B6)
private val SolarFlare = Color(0xFFFFF785)
// Light glaucous and light solar tints, then the full colors.
private val DayAccents = Accents(
    explorer = Accent(Color(0xFFDCE4F2), Sapphire),
    play = Accent(Color(0xFFFFFBC9), Sapphire),
    report = Accent(Glaucous, Color.White),
    notes = Accent(SolarFlare, Sapphire),
)

val LocalAccents = staticCompositionLocalOf { NightAccents }

private val Night = darkColorScheme(
    primary = Color(0xFFE07A5F),
    onPrimary = Color.White,
    secondary = Color(0xFF3D7BFF),
    onSecondary = Color.White,
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFEEF1F8),
    surface = Color(0xFF0B1020),
    onSurface = Color(0xFFEEF1F8),
    onSurfaceVariant = Color(0xFF9AA3BD),
    surfaceContainerLow = Color(0xFF151B2E),
    surfaceContainer = Color(0xFF151B2E),
    surfaceContainerHigh = Color(0xFF1E2640),
    surfaceContainerHighest = Color(0xFF1E2640),
    secondaryContainer = Color(0xFF151B2E),
    onSecondaryContainer = Color(0xFFEEF1F8),
    outline = Color(0xFF2A3352),
    outlineVariant = Color(0xFF2A3352),
    error = Color(0xFFFF7A7A),
)

private val Day = lightColorScheme(
    primary = Sapphire,
    onPrimary = SolarFlare,
    secondary = Glaucous,
    onSecondary = Color.White,
    // Cool stone background; plain buttons and cards a step lighter.
    background = Color(0xFFECECE8),
    onBackground = Sapphire,
    surface = Color(0xFFECECE8),
    onSurface = Sapphire,
    onSurfaceVariant = Color(0xFF5A6B8C),
    surfaceContainerLow = Color(0xFFF6F6F3),
    surfaceContainer = Color(0xFFF6F6F3),
    surfaceContainerHigh = Color(0xFFE2E2DD),
    surfaceContainerHighest = Color(0xFFD9D9D3),
    secondaryContainer = Color(0xFFF6F6F3),
    onSecondaryContainer = Sapphire,
    outline = Color(0xFFD9D9D3),
    outlineVariant = Color(0xFFD9D9D3),
    error = Color(0xFFC62828),
)

// Variable fonts in res/font; each Font picks its weight from the one file.
private fun family(res: Int, vararg weights: Int) = FontFamily(weights.map { Font(res, FontWeight(it)) })

private val Display = family(R.font.unbounded, 500, 600, 700)
private val Sans = family(R.font.ibm_plex_sans, 400, 500, 600)
val Mono = family(R.font.jetbrains_mono, 400, 500, 600)

private val Type = Typography().run {
    fun TextStyle.display() = copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    fun TextStyle.sans() = copy(fontFamily = Sans)
    copy(
        displayLarge = displayLarge.display(),
        displayMedium = displayMedium.display(),
        displaySmall = displaySmall.display(),
        headlineLarge = headlineLarge.display(),
        headlineMedium = headlineMedium.display(),
        headlineSmall = headlineSmall.display(),
        titleLarge = titleLarge.display().copy(fontSize = 20.sp),
        titleMedium = titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.sans(),
        bodyLarge = bodyLarge.sans(),
        bodyMedium = bodyMedium.sans(),
        bodySmall = bodySmall.sans(),
        labelLarge = labelLarge.sans(),
        labelMedium = labelMedium.sans(),
        labelSmall = labelSmall.copy(fontFamily = Mono, letterSpacing = 1.4.sp),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The chosen theme mode, saved in SharedPreferences. Changes apply at once. */
object ThemeStore {
    private const val KEY = "theme"
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(c: Context) {
        mode = ThemeMode.entries.firstOrNull { it.name == prefs(c).getString(KEY, null) } ?: ThemeMode.SYSTEM
    }

    fun save(c: Context, m: ThemeMode) {
        mode = m
        prefs(c).edit().putString(KEY, m.name).apply()
    }
}

@Composable
fun RocketTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    remember { ThemeStore.load(view.context) }
    val dark = when (ThemeStore.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Status and navigation bar icons follow the app theme, not the system one.
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalBoardColors provides NightBoard, LocalAccents provides if (dark) NightAccents else DayAccents) {
        MaterialTheme(colorScheme = if (dark) Night else Day, typography = Type, content = content)
    }
}
