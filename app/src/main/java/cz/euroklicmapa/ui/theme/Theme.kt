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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import cz.euroklicmapa.data.prefs.DesignVariant

internal val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightAccent,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightSuccess,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
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

internal val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkAccent,
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkSuccess,
    onTertiary = Color(0xFF07231A),
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
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
    val warning: Color,
    val accent: Color,
    val textStrong: Color,
    val brandButton: Color,
    val onBrandButton: Color,
    val scrim: Color,
    val cardSurface: Color = LightSurface,
    val navBackground: Color = LightSurfaceContainer,
    val navText: Color = LightOnSurfaceVariant,
    val navActive: Color = LightPrimary,
    val navIndicator: Color = LightPrimaryContainer,
    val onNavIndicator: Color = LightOnPrimaryContainer,
    val chipBackground: Color = LightSurface,
    val chipText: Color = LightOnSurfaceVariant,
    val selectedChip: Color = BrandBlue,
    val onSelectedChip: Color = Color.White,
    val hero: Color = LightPrimaryContainer,
    val onHero: Color = LightOnPrimaryContainer,
    val key: Color = LightSurfaceContainerHigh,
    val onKey: Color = LightOnPrimaryContainer,
    val cluster: Color = BrandBlue,
    val onCluster: Color = Color.White,
    val clusterRing: Color = Color.White,
    val selectionHalo: Color = BrandBlue,
    val userDot: Color = Color(0xFF0D8259),
    val userHalo: Color = Color(0xFF0D8259).copy(alpha = 55f / 255f),
    val userRing: Color = Color.White,
    val verifiedText: Color = LightOnTertiaryContainer,
    val reportedText: Color = LightOnSecondaryContainer,
)

internal val LightExtended = ExtendedColors(
    success = LightSuccess,
    onSuccess = Color.White,
    warning = LightWarning,
    accent = LightAccent,
    textStrong = LightTextStrong,
    brandButton = BrandBlue,
    onBrandButton = Color.White,
    scrim = Color(0x14000000),
)

internal val DarkExtended = ExtendedColors(
    success = DarkSuccess,
    onSuccess = Color(0xFF07231A),
    warning = DarkWarning,
    accent = DarkAccent,
    textStrong = DarkTextStrong,
    brandButton = BrandBlue,
    onBrandButton = Color.White,
    scrim = Color(0x33000000),
    cardSurface = DarkSurface,
    navBackground = DarkSurfaceContainer,
    navText = DarkOnSurfaceVariant,
    navActive = DarkPrimary,
    navIndicator = DarkPrimaryContainer,
    onNavIndicator = DarkOnPrimaryContainer,
    chipBackground = DarkSurface,
    chipText = DarkOnSurfaceVariant,
    hero = DarkPrimaryContainer,
    onHero = DarkOnPrimaryContainer,
    key = DarkSurfaceContainerHigh,
    onKey = DarkOnPrimaryContainer,
    verifiedText = DarkOnTertiaryContainer,
    reportedText = DarkOnSecondaryContainer,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

/** `EuroklicTheme.extended.success` etc. */
object EuroklicTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
    val shapes: ExtendedShapes
        @Composable get() = LocalExtendedShapes.current
    val variant: DesignVariant
        @Composable get() = LocalDesignVariant.current
}

val LocalDesignVariant = staticCompositionLocalOf { DesignVariant.SOUCASNY }

/**
 * Dynamic color is intentionally NOT used: the brand palette is contrast-verified and must
 * stay stable across devices to match the website.
 */
@Composable
fun EuroklicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: DesignVariant = DesignVariant.SOUCASNY,
    content: @Composable () -> Unit,
) {
    val palette = remember(variant, darkTheme) { designPalette(variant, darkTheme) }
    val shapes = remember(variant) { designShapes(variant) }
    val typography = remember(variant) { designTypography(variant) }
    val materialShapes = remember(variant) { designMaterialShapes(variant) }
    val colors = palette.colors
    val extended = palette.extended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalExtendedShapes provides shapes,
        LocalDesignVariant provides variant,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = materialShapes,
            content = content,
        )
    }
}
