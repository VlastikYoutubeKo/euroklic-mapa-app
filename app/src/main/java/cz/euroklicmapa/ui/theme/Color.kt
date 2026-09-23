package cz.euroklicmapa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette — "2.0" revision (2026-09-18), built from a pasted design spec's proposed
 * background/surface/status colors. The spec's own Primary (#3F6FF5) FAILED WCAG AA as white
 * button text (4.37:1, needs 4.5) and as text-on-dark-background (4.33:1) — every value below
 * was re-derived and verified (30-pair audit, all ≥4.5:1 body text / ≥3:1 UI, most comfortably
 * above minimum) before being adopted. Do not tweak without recomputing contrast.
 */

// Shared (identical in light and dark)
val BrandBlue = Color(0xFF2C60F4)        // filled buttons / FAB background — same in both modes
val BrandBluePressed = Color(0xFF1E4CD6)

// ---- Light ----
val LightPrimary = BrandBlue
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDCE5FF)
val LightOnPrimaryContainer = Color(0xFF0D285F)
val LightBackground = Color(0xFFF8F9FC)
val LightOnBackground = Color(0xFF171B23)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF171B23)
val LightOnSurfaceVariant = Color(0xFF454C5B)   // text-muted
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFCFCFD)
val LightSurfaceContainer = Color(0xFFEEF1F7)
val LightSurfaceContainerHigh = Color(0xFFE4E8F0)
val LightSurfaceContainerHighest = Color(0xFFDAE1EE)
val LightOutline = Color(0xFF737B8B)
val LightOutlineVariant = Color(0xFFE9EAEC)
val LightTextStrong = Color(0xFF0E141D)
val LightSuccess = Color(0xFF157A54)
val LightWarning = Color(0xFF94640B)            // "ověření je starší" (STALE) caption
val LightError = Color(0xFFE00014)
val LightAccent = Color(0xFFB4530A)             // community-added
val LightSecondaryContainer = Color(0xFFFFE3C7) // secondary (accent-orange) container — 10.70:1
val LightOnSecondaryContainer = Color(0xFF4A2800)
val LightTertiaryContainer = Color(0xFFC8F0DC)  // tertiary (success-green) container — 11.18:1
val LightOnTertiaryContainer = Color(0xFF04341F)

// ---- Dark ----
val DarkPrimary = Color(0xFF5C85F7)             // text / link / active state on dark surfaces
val DarkOnPrimary = Color(0xFF0B1220)
val DarkPrimaryContainer = Color(0xFF2447A8)
val DarkOnPrimaryContainer = Color(0xFFDCE5FF)
val DarkBackground = Color(0xFF07111F)
val DarkOnBackground = Color(0xFFF0F3FA)
val DarkSurface = Color(0xFF0D192B)
val DarkOnSurface = Color(0xFFF0F3FA)
val DarkOnSurfaceVariant = Color(0xFFB8C2D5)    // text-muted
val DarkSurfaceContainerLowest = Color(0xFF0A121D)
val DarkSurfaceContainerLow = Color(0xFF122033)
val DarkSurfaceContainer = Color(0xFF14243A)
val DarkSurfaceContainerHigh = Color(0xFF1B2C45)
val DarkSurfaceContainerHighest = Color(0xFF1D3453)
val DarkOutline = Color(0xFF78869D)
val DarkOutlineVariant = Color(0xFF2C323C)
val DarkTextStrong = Color(0xFFF8FAFC)
val DarkSuccess = Color(0xFF22C98A)
val DarkWarning = Color(0xFFF2B84B)             // "ověření je starší" (STALE) caption
val DarkError = Color(0xFFFF6B78)
val DarkAccent = Color(0xFFF0A63E)
val DarkSecondaryContainer = Color(0xFF4A3010)  // secondary (accent-orange) container — 9.91:1
val DarkOnSecondaryContainer = Color(0xFFFFE3C7)
val DarkTertiaryContainer = Color(0xFF123D2C)   // tertiary (success-green) container — 9.80:1
val DarkOnTertiaryContainer = Color(0xFFC8F0DC)

// ---- Marker colours ----
// Per the 2026-09-02 backend change, the 4 raw sources collapse to 2 UI groups. Colour + main
// label follow the GROUP; the raw source shows only as a detail note ("Původní zdroj: …").
val ColorOfficial = Color(0xFF3B82F6)   // source == "cd"
val ColorCommunity = Color(0xFFF59E0B)  // osm / mapotic / user
val ColorPickup = Color(0xFF64748B)

/** `"cd"` → true (official); everything else → community. Matches backend `source_group`. */
fun isOfficialSource(source: String?): Boolean = source?.lowercase() == "cd"

fun sourceColor(source: String?): Color =
    if (isOfficialSource(source)) ColorOfficial else ColorCommunity

/** Group label — the main label on markers, chips and cards. */
fun sourceLabel(source: String?): String =
    if (isOfficialSource(source)) "Oficiální zdroj" else "Komunitní zdroj"

/** The specific origin, shown only as a detail on the place screen. */
fun originalSourceLabel(source: String?): String = when (source?.lowercase()) {
    "cd" -> "České dráhy"
    "osm" -> "OpenStreetMap"
    "mapotic" -> "WCkompas (Mapotic)"
    "user" -> "Přidáno uživatelem"
    else -> source ?: "Neznámý zdroj"
}
