package cz.euroklicmapa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import cz.euroklicmapa.R

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
