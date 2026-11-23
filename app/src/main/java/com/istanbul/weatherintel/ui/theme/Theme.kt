package com.istanbul.weatherintel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.istanbul.weatherintel.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF356FCB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9FC),
    onPrimaryContainer = Color(0xFF183B70),
    secondary = Color(0xFF526A89),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF18263A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18263A),
    surfaceVariant = Color(0xFFE8EEF6),
    onSurfaceVariant = Color(0xFF5C6B80),
    outline = Color(0xFFD2DCE8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DB8F4),
    onPrimary = Color(0xFF10233F),
    primaryContainer = Color(0xFF29466C),
    onPrimaryContainer = Color(0xFFE0ECFF),
    secondary = Color(0xFFA8B8CC),
    onSecondary = Color(0xFF1B2A3E),
    background = Color(0xFF111B2A),
    onBackground = Color(0xFFE7EDF6),
    surface = Color(0xFF1A2739),
    onSurface = Color(0xFFE7EDF6),
    surfaceVariant = Color(0xFF26364B),
    onSurfaceVariant = Color(0xFFB2C0D1),
    outline = Color(0xFF43556D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/**
 * Sky gradient used by the reference-style header and the Home hero.
 *
 * The three stops are ordered from the top of the header downwards. The
 * header fades from [0] to [1] and the Home hero continues from [1] to [2],
 * so the two surfaces read as one continuous sky when Home is scrolled to the
 * top.
 */
object SkyPalette {
    val light = listOf(
        Color(0xFF3E82CE),
        Color(0xFFA9CFF0),
        Color(0xFFE3EEF9),
    )

    val dark = listOf(
        Color(0xFF12304F),
        Color(0xFF1C3A5E),
        Color(0xFF1A2C43),
    )
}

/** True while the dark colour scheme is active. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** SYSTEM follows Android; LIGHT and DARK are explicit persisted choices. */
@Composable
fun IstanbulWeatherTheme(
    themeMode: ThemeMode,
    headerDarkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = when {
        headerDarkOverride != null -> headerDarkOverride
        themeMode == ThemeMode.DARK -> true
        themeMode == ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
