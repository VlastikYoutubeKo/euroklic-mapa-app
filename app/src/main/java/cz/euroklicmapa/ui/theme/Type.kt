package cz.euroklicmapa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import cz.euroklicmapa.R
import cz.euroklicmapa.data.prefs.DesignVariant

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val plexName = GoogleFont("IBM Plex Sans")

val IBMPlexSans = FontFamily(
    Font(googleFont = plexName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = plexName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = plexName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = plexName, fontProvider = provider, weight = FontWeight.Bold),
)

private fun plex(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = IBMPlexSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displaySmall = plex(FontWeight.Bold, 34, 40, (-0.5)),
    headlineMedium = plex(FontWeight.Bold, 26, 32, (-0.25)),
    headlineSmall = plex(FontWeight.SemiBold, 22, 28),
    titleLarge = plex(FontWeight.SemiBold, 20, 26),
    titleMedium = plex(FontWeight.SemiBold, 16, 22, 0.1),
    titleSmall = plex(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = plex(FontWeight.Normal, 16, 24, 0.15),
    bodyMedium = plex(FontWeight.Normal, 14, 20, 0.15),
    bodySmall = plex(FontWeight.Normal, 12, 16, 0.2),
    labelLarge = plex(FontWeight.SemiBold, 14, 18, 0.1),
    labelMedium = plex(FontWeight.SemiBold, 12, 16, 0.4),
    labelSmall = plex(FontWeight.Medium, 11, 14, 0.4),
)

private fun downloadableFamily(name: String) = FontFamily(
    Font(GoogleFont(name), provider, weight = FontWeight.Normal),
    Font(GoogleFont(name), provider, weight = FontWeight.Medium),
    Font(GoogleFont(name), provider, weight = FontWeight.SemiBold),
    Font(GoogleFont(name), provider, weight = FontWeight.Bold),
)

// This family provides regular and bold; let font matching choose those for intermediate weights.
private val atkinson = FontFamily(
    Font(GoogleFont("Atkinson Hyperlegible"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("Atkinson Hyperlegible"), provider, weight = FontWeight.Bold),
)
private val publicSans = downloadableFamily("Public Sans")
private val overpass = downloadableFamily("Overpass")

internal fun designTypography(variant: DesignVariant): Typography {
    if (variant == DesignVariant.SOUCASNY) return Typography
    val family = when (variant) {
        DesignVariant.KONTRAST -> atkinson
        DesignVariant.SVEZI -> publicSans
        DesignVariant.ZNACENI -> overpass
        else -> IBMPlexSans
    }
    val extra = if (variant == DesignVariant.KONTRAST) 2f else 0f
    fun TextStyle.adapt() = copy(
        fontFamily = family, fontSize = (fontSize.value + extra).sp, lineHeight = (lineHeight.value + extra).sp,
        fontWeight = if (variant == DesignVariant.KONTRAST && fontWeight == FontWeight.SemiBold) FontWeight.Bold else fontWeight,
    )
    return Typography.copy(
        displayLarge = Typography.displayLarge.adapt(), displayMedium = Typography.displayMedium.adapt(),
        displaySmall = Typography.displaySmall.adapt(), headlineLarge = Typography.headlineLarge.adapt(),
        headlineMedium = Typography.headlineMedium.adapt(), headlineSmall = Typography.headlineSmall.adapt(),
        titleLarge = Typography.titleLarge.adapt(), titleMedium = Typography.titleMedium.adapt(), titleSmall = Typography.titleSmall.adapt(),
        bodyLarge = Typography.bodyLarge.adapt(), bodyMedium = Typography.bodyMedium.adapt(), bodySmall = Typography.bodySmall.adapt(),
        labelLarge = Typography.labelLarge.adapt(), labelMedium = Typography.labelMedium.adapt(), labelSmall = Typography.labelSmall.adapt(),
    )
}
