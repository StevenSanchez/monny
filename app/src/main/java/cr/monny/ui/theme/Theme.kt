package cr.monny.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cr.monny.data.ThemeMode

/*
 * Tema de Material 3 con colores, formas y tipografía propias.
 * Soporta modo claro, oscuro y automático según el sistema.
 */

private val LightScheme = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,
    secondary = Amber700,
    onSecondary = Color.White,
    secondaryContainer = Amber200,
    onSecondaryContainer = Color(0xFF2B1D00),
    tertiary = Color(0xFF3A6B8C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFE4F5),
    onTertiaryContainer = Color(0xFF08243A),
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEAE2),
    onSurfaceVariant = Color(0xFF46483F),
    surfaceContainer = Color(0xFFF4F1E9),
    surfaceContainerHigh = Color(0xFFEFEBE2),
    outline = Color(0xFFD3CEC2),
    outlineVariant = Color(0xFFE5E1D6),
    error = Negative,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Green300,
    onPrimary = Green900,
    primaryContainer = Color(0xFF164A3B),
    onPrimaryContainer = Green100,
    secondary = Amber500,
    onSecondary = Color(0xFF2B1D00),
    secondaryContainer = Color(0xFF5A3E05),
    onSecondaryContainer = Amber200,
    tertiary = Color(0xFF93C6E8),
    onTertiary = Color(0xFF08243A),
    tertiaryContainer = Color(0xFF1F4159),
    onTertiaryContainer = Color(0xFFCFE4F5),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE3E6E1),
    surface = Color(0xFF161D19),
    onSurface = Color(0xFFE3E6E1),
    surfaceVariant = Color(0xFF232B26),
    onSurfaceVariant = Color(0xFFC2C8C2),
    surfaceContainer = Color(0xFF1B231E),
    surfaceContainerHigh = Color(0xFF212A24),
    outline = Color(0xFF3C463F),
    outlineVariant = Color(0xFF2C3530),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

data class MonnyExtraColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val positiveContainer: Color,
    val negativeContainer: Color,
)

val LocalMonnyColors = staticCompositionLocalOf {
    MonnyExtraColors(
        positive = Positive,
        negative = Negative,
        warning = Warning,
        positiveContainer = Color(0xFFDDF3E5),
        negativeContainer = Color(0xFFFADAD7),
    )
}

@Composable
fun MonnyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = if (dark) {
        MonnyExtraColors(
            positive = Color(0xFF7BD5A5),
            negative = Color(0xFFFFB4AB),
            warning = Color(0xFFF3C77B),
            positiveContainer = Color(0xFF15301F),
            negativeContainer = Color(0xFF3B1512),
        )
    } else {
        MonnyExtraColors(
            positive = Positive,
            negative = Negative,
            warning = Warning,
            positiveContainer = Color(0xFFDDF3E5),
            negativeContainer = Color(0xFFFADAD7),
        )
    }
    CompositionLocalProvider(LocalMonnyColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            shapes = MonnyShapes,
            typography = MonnyTypography,
            content = content,
        )
    }
}
