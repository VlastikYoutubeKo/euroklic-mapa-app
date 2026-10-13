package cz.euroklicmapa.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import cz.euroklicmapa.data.prefs.DesignVariant
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastTest {
    @Test
    fun userLocationHasVisibleBoundaryOnLightRasterTiles() {
        val map = Color(0xFFE3E9E1)
        for (variant in DesignVariant.entries) for (dark in listOf(false, true)) {
            val e = designPalette(variant, dark).extended
            // The white high-contrast dark dot has a black perimeter against the light tiles.
            val boundary = if (variant == DesignVariant.KONTRAST && dark) e.userRing else e.userDot
            assertTrue("$variant dark=$dark location/map contrast: ${ratio(boundary, map)}", ratio(boundary, map) >= 3.0)
        }
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double = value.toDouble().let {
            if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    @Test
    fun allEightSchemesMeetWcagAa() {
        val failures = mutableListOf<String>()
        for (variant in DesignVariant.entries) for (dark in listOf(false, true)) {
            val (c, e) = designPalette(variant, dark)
            fun check(name: String, foreground: Color, background: Color, minimum: Double = 4.5) {
                val actual = ratio(foreground, background)
                if (actual + 0.00001 < minimum) failures += "$variant dark=$dark $name: $actual < $minimum"
            }
            check("photo caption/white photo", PhotoCaptionText, PhotoCaptionScrim.compositeOver(Color.White))
            check("brandButton boundary/surface", e.brandButton, c.surface, 3.0)
            listOf(
                Triple("primary", c.onPrimary, c.primary),
                Triple("primaryContainer", c.onPrimaryContainer, c.primaryContainer),
                Triple("secondary", c.onSecondary, c.secondary),
                Triple("secondaryContainer", c.onSecondaryContainer, c.secondaryContainer),
                Triple("tertiary", c.onTertiary, c.tertiary),
                Triple("tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer),
                Triple("error", c.onError, c.error),
                Triple("errorContainer", c.onErrorContainer, c.errorContainer),
                Triple("surfaceVariant", c.onSurfaceVariant, c.surfaceVariant),
                Triple("background", c.onBackground, c.background),
                Triple("onSurface/background", c.onSurface, c.background),
                Triple("inverseSurface", c.inverseOnSurface, c.inverseSurface),
                Triple("brandButton", e.onBrandButton, e.brandButton),
                Triple("success", e.onSuccess, e.success),
                Triple("navText", e.navText, e.navBackground),
                Triple("navActive", e.navActive, e.navBackground),
                Triple("navIndicator", e.onNavIndicator, e.navIndicator),
                Triple("chip", e.chipText, e.chipBackground),
                Triple("selectedChip", e.onSelectedChip, e.selectedChip),
                Triple("hero", e.onHero, e.hero),
                Triple("key", e.onKey, e.key),
                Triple("cluster", e.onCluster, e.cluster),
            ).forEach { (name, fg, bg) -> check(name, fg, bg) }
            val surfaces = listOf(
                "surface" to c.surface, "card" to e.cardSurface, "background" to c.background,
                "containerLow" to c.surfaceContainerLow, "container" to c.surfaceContainer,
                "containerHigh" to c.surfaceContainerHigh, "containerHighest" to c.surfaceContainerHighest,
            )
            for ((name, bg) in surfaces) {
                check("onSurface/$name", c.onSurface, bg)
                check("onSurfaceVariant/$name", c.onSurfaceVariant, bg)
                check("textStrong/$name", e.textStrong, bg)
            }
            for ((name, bg) in listOf("surface" to c.surface, "background" to c.background, "card" to e.cardSurface)) {
                check("verified badge/$name", e.verifiedText, e.success.copy(alpha = 0.14f).compositeOver(bg))
                check("reported badge/$name", e.reportedText, e.accent.copy(alpha = 0.16f).compositeOver(bg))
                check("active positive vote/$name", e.verifiedText, e.success.copy(alpha = 0.12f).compositeOver(bg))
                check("active negative vote/$name", c.onErrorContainer, c.error.copy(alpha = 0.12f).compositeOver(bg))
                check("primary/$name", c.primary, bg)
                check("success/$name", e.success, bg)
                check("warning/$name", e.warning, bg)
                check("accent/$name", e.accent, bg)
                check("error/$name", c.error, bg)
                check("outline/$name", c.outline, bg, 3.0)
            }
            // outlineVariant is a decorative separator in the original design, not a text/UI colour.
            if (variant == DesignVariant.KONTRAST) check("outlineVariant/surface", c.outlineVariant, c.surface, 3.0)
            if (variant == DesignVariant.SOUCASNY) {
                check("wheelchair yes/key", e.verifiedText, e.key)
                check("wheelchair no/key", c.onErrorContainer, e.key)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
