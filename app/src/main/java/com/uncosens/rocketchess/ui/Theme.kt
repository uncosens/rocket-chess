package com.uncosens.rocketchess.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Board square colors. [lastLight]/[lastDark] mark the last move. */
data class BoardColors(val light: Color, val dark: Color, val lastLight: Color, val lastDark: Color)

val LocalBoardColors = staticCompositionLocalOf { NightBoard }

// "Night Launch" for dark mode, "Daylight" for light mode.
private val NightBoard = BoardColors(Color(0xFFCBD2E1), Color(0xFF59678A), Color(0xFFF2B48F), Color(0xFFC98765))
private val DayBoard = BoardColors(Color(0xFFE8EDF4), Color(0xFF8DA2C4), Color(0xFFC3CEF7), Color(0xFF7D93E2))

private val Night = darkColorScheme(
    primary = Color(0xFFFF6B35),
    onPrimary = Color.White,
    secondary = Color(0xFFFFD166),
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
    primary = Color(0xFF2F54EB),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B35),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF12151C),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF12151C),
    onSurfaceVariant = Color(0xFF5D6474),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF0F4),
    surfaceContainerHighest = Color(0xFFE3E6EC),
    secondaryContainer = Color(0xFFE9EDFD),
    onSecondaryContainer = Color(0xFF12151C),
    outline = Color(0xFFE3E6EC),
    outlineVariant = Color(0xFFE3E6EC),
    error = Color(0xFFC62828),
)

private val Type = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 1.4.sp),
    )
}

@Composable
fun RocketTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalBoardColors provides if (dark) NightBoard else DayBoard) {
        MaterialTheme(colorScheme = if (dark) Night else Day, typography = Type, content = content)
    }
}
