package cz.euroklicmapa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import cz.euroklicmapa.data.prefs.DesignVariant

internal data class DesignPalette(val colors: ColorScheme, val extended: ExtendedColors)

private fun hex(value: Long) = Color(0xFF000000 or value)

/** Light values follow EK_THEMES / EK_DETAIL; dark values retain each variant's identity. */
internal fun designPalette(variant: DesignVariant, dark: Boolean): DesignPalette {
    val base = if (dark) DarkColors else LightColors
    val ext = if (dark) DarkExtended else LightExtended
    if (variant == DesignVariant.SOUCASNY) return DesignPalette(base, ext)

    val navy = hex(if (variant == DesignVariant.ZNACENI) 0x003A70 else 0x0B1F4D)
    val yellow = hex(0xFFD400)
    val background: Color
    val surface: Color
    val text: Color
    val muted: Color
    val primary: Color
    val onPrimary: Color
    val container: Color
    val onContainer: Color
    val success: Color
    val softSuccess: Color
    val outline: Color
    val border: Color
    when (variant) {
        DesignVariant.KONTRAST -> {
            background = if (dark) Color.Black else Color.White
            surface = background
            text = if (dark) Color.White else Color.Black
            muted = if (dark) hex(0xE5E5E5) else hex(0x262626)
            primary = if (dark) yellow else navy
            onPrimary = if (dark) Color.Black else Color.White
            container = navy
            onContainer = Color.White
            success = hex(if (dark) 0x62E49D else 0x0A6B3D)
            softSuccess = background
            outline = text
            border = text
        }
        DesignVariant.SVEZI -> {
            background = hex(if (dark) 0x0A1710 else 0xF3F6F4)
            surface = hex(if (dark) 0x12251A else 0xFFFFFF)
            text = hex(if (dark) 0xEDF8F0 else 0x14211A)
            muted = hex(if (dark) 0xBFCEC3 else 0x46564D)
            primary = hex(if (dark) 0x75D7A0 else 0x1B7A4B)
            onPrimary = hex(if (dark) 0x062C19 else 0xFFFFFF)
            container = hex(if (dark) 0x12452B else 0xD4EEDF)
            onContainer = hex(if (dark) 0xD4EEDF else 0x0B3B22)
            success = hex(if (dark) 0x75D7A0 else 0x1B7A4B)
            softSuccess = container
            outline = hex(if (dark) 0x829A8C else 0x687E70)
            border = hex(if (dark) 0x3A5545 else 0xDCE5DF)
        }
        DesignVariant.ZNACENI -> {
            background = hex(if (dark) 0x081421 else 0xF2F4F7)
            surface = hex(if (dark) 0x0E2235 else 0xFFFFFF)
            text = hex(if (dark) 0xEFF6FF else 0x0E1A2B)
            muted = hex(if (dark) 0xBDCBDB else 0x44505F)
            primary = hex(if (dark) 0x8CC7FF else 0x003A70)
            onPrimary = hex(if (dark) 0x00203B else 0xFFFFFF)
            container = navy
            onContainer = Color.White
            success = hex(if (dark) 0x69D9A7 else 0x157A54)
            softSuccess = hex(if (dark) 0x123D2C else 0xC8F0DC)
            outline = hex(if (dark) 0x829BB5 else 0x66768A)
            border = hex(if (dark) 0x39536D else 0xC9D1DC)
        }
        DesignVariant.SOUCASNY -> error("Handled above")
    }
    val key = when (variant) {
        DesignVariant.KONTRAST -> yellow
        DesignVariant.ZNACENI -> hex(0xFFC629)
        else -> hex(if (dark) 0x4A3010 else 0xFFF0D9)
    }
    val onKey = when (variant) {
        DesignVariant.KONTRAST -> Color.Black
        DesignVariant.ZNACENI -> hex(0x0E1A2B)
        else -> hex(if (dark) 0xFFE3C7 else 0x4A2800)
    }
    val colors = base.copy(
        primary = primary, onPrimary = onPrimary, surfaceTint = primary,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondaryContainer = key, onSecondaryContainer = onKey,
        tertiary = success, onTertiary = if (dark) hex(0x062C19) else Color.White,
        tertiaryContainer = softSuccess,
        onTertiaryContainer = if (variant == DesignVariant.KONTRAST) text else hex(if (dark) 0xD4EEDF else 0x04341F),
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = if (dark) surface else background, onSurfaceVariant = muted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = if (dark) surface else background,
        surfaceContainerHigh = if (dark) surface else background,
        surfaceContainerHighest = if (dark) surface else background,
        outline = outline, outlineVariant = border,
    )
    val navBackground = when (variant) {
        DesignVariant.ZNACENI -> navy
        else -> surface
    }
    val navIndicator = when (variant) {
        DesignVariant.KONTRAST -> yellow
        DesignVariant.ZNACENI -> Color.White
        else -> container
    }
    val onNavIndicator = when (variant) {
        DesignVariant.KONTRAST -> Color.Black
        DesignVariant.ZNACENI -> navy
        else -> onContainer
    }
    val brand = when (variant) {
        DesignVariant.KONTRAST -> if (dark) yellow else navy
        DesignVariant.SVEZI -> hex(0x1B7A4B)
        else -> if (dark) hex(0x246FBA) else navy
    }
    val cluster = if (variant == DesignVariant.KONTRAST || variant == DesignVariant.ZNACENI) navy else brand
    val user = when (variant) {
        DesignVariant.KONTRAST -> if (dark) Color.White else Color.Black
        DesignVariant.ZNACENI -> hex(0x0D8259)
        // Raster tiles stay light in dark mode too: use the dark green, not a pale text/link green.
        else -> brand
    }
    return DesignPalette(colors, ext.copy(
        success = success, onSuccess = colors.onTertiary,
        textStrong = if (variant == DesignVariant.SVEZI && !dark) hex(0x0B1610) else text,
        brandButton = brand, onBrandButton = if (variant == DesignVariant.KONTRAST && dark) Color.Black else Color.White,
        cardSurface = if (variant == DesignVariant.SVEZI && !dark) hex(0xF7FAF8) else surface,
        navBackground = navBackground,
        navText = if (variant == DesignVariant.ZNACENI) hex(0xC9D6E8) else muted,
        navActive = when (variant) {
            DesignVariant.KONTRAST -> text
            DesignVariant.ZNACENI -> Color.White
            else -> onContainer
        },
        navIndicator = navIndicator, onNavIndicator = onNavIndicator,
        chipBackground = if (variant == DesignVariant.ZNACENI) hex(0x0F4C86) else surface,
        chipText = if (variant == DesignVariant.ZNACENI) Color.White else text,
        selectedChip = navIndicator, onSelectedChip = onNavIndicator,
        hero = container, onHero = onContainer, key = key, onKey = onKey,
        cluster = cluster, onCluster = Color.White,
        clusterRing = if (variant == DesignVariant.KONTRAST) yellow else Color.White,
        selectionHalo = if (variant == DesignVariant.KONTRAST) yellow else primary,
        userDot = user, userHalo = (if (variant == DesignVariant.KONTRAST) yellow else user)
            .copy(alpha = when (variant) {
                DesignVariant.KONTRAST -> 0.55f
                DesignVariant.ZNACENI -> 55f / 255f
                else -> 0.18f
            }),
        userRing = if (variant == DesignVariant.KONTRAST && dark) Color.Black else Color.White,
    ))
}
