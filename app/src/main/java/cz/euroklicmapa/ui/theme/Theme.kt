package cz.euroklicmapa.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightAccent,
    onSecondary = Color.White,
    tertiary = LightSuccess,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkAccent,
    onSecondary = Color(0xFF1A1A1A),
    tertiary = DarkSuccess,
    onTertiary = Color(0xFF07231A),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = Color(0xFF3B0A0A),
)

/** Brand semantics Material 3 has no dedicated slot for. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val accent: Color,
    val textStrong: Color,
    val brandButton: Color,
    val onBrandButton: Color,
    val scrim: Color,
)

private val LightExtended = ExtendedColors(
    success = LightSuccess,
    onSuccess = Color.White,
    accent = LightAccent,
    textStrong = LightTextStrong,
    brandButton = BrandBlue,
    onBrandButton = Color.White,
    scrim = Color(0x14000000),
)

private val DarkExtended = ExtendedColors(
    success = DarkSuccess,
    onSuccess = Color(0xFF07231A),
    accent = DarkAccent,
    textStrong = DarkTextStrong,
    brandButton = BrandBlue,
    onBrandButton = Color.White,
    scrim = Color(0x33000000),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

/** `EuroklicTheme.extended.success` etc. */
object EuroklicTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

/**
 * Dynamic color is intentionally NOT used: the brand palette is contrast-verified and must
 * stay stable across devices to match the website.
 */
@Composable
fun EuroklicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}
