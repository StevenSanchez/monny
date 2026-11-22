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
    inversePrimary = Green300,
    secondary = Amber700,
    onSecondary = Color.White,
    secondaryContainer = Amber200,
    onSecondaryContainer = Color(0xFF332600),
    tertiary = Color(0xFF3A6B8C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFE4F5),
    onTertiaryContainer = Color(0xFF08243A),
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEE9DE),
    onSurfaceVariant = Color(0xFF5B6159),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F4EC),
    surfaceContainer = Color(0xFFF4EFE6),
    surfaceContainerHigh = Color(0xFFEEE8DC),
    surfaceContainerHighest = Color(0xFFE8E1D4),
    surfaceTint = Green700,
    outline = Color(0xFFDCD4C4),
    outlineVariant = Color(0xFFE9E2D4),
    error = Negative,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Green300,
    onPrimary = Green900,
    primaryContainer = Color(0xFF174A39),
    onPrimaryContainer = Green100,
    inversePrimary = Green700,
    secondary = GoldSoft,
    onSecondary = Color(0xFF2B2200),
    secondaryContainer = Color(0xFF4A3A08),
    onSecondaryContainer = Amber200,
    tertiary = Color(0xFF93C6E8),
    onTertiary = Color(0xFF08243A),
    tertiaryContainer = Color(0xFF1F4159),
    onTertiaryContainer = Color(0xFFCFE4F5),
    background = Color(0xFF0A110D),
    onBackground = Color(0xFFE4E9E2),
    surface = Color(0xFF121B16),
    onSurface = Color(0xFFE4E9E2),
    surfaceVariant = Color(0xFF232B25),
    onSurfaceVariant = Color(0xFFC0C8C0),
    surfaceContainerLowest = Color(0xFF070D0A),
    surfaceContainerLow = Color(0xFF101814),
    surfaceContainer = Color(0xFF161F1A),
    surfaceContainerHigh = Color(0xFF1C2620),
    surfaceContainerHighest = Color(0xFF222D26),
    surfaceTint = Green300,
    outline = Color(0xFF3B463E),
    outlineVariant = Color(0xFF2A342D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

data class MonnyExtraColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val positiveContainer: Color,
    val negativeContainer: Color,
    val gold: Color,
)

val LocalMonnyColors = staticCompositionLocalOf {
    MonnyExtraColors(
        positive = Positive,
        negative = Negative,
        warning = Warning,
        positiveContainer = Color(0xFFDDF3E5),
        negativeContainer = Color(0xFFFADAD7),
        gold = Gold,
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
            gold = GoldSoft,
        )
    } else {
        MonnyExtraColors(
            positive = Positive,
            negative = Negative,
            warning = Warning,
            positiveContainer = Color(0xFFDDF3E5),
            negativeContainer = Color(0xFFFADAD7),
            gold = Gold,
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
