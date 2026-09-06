package cz.euroklicmapa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette. Contrast was verified against WCAG AA (4.5:1 for body text).
 * Do not tweak these values without recomputing contrast.
 */

// Shared (identical in light and dark)
val BrandBlue = Color(0xFF2454E0)        // filled buttons / FAB background — same in both modes
val BrandBluePressed = Color(0xFF1B3FB8)

// ---- Light ----
val LightPrimary = BrandBlue
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDDE6FF)
val LightOnPrimaryContainer = Color(0xFF0C246E)
val LightBackground = Color(0xFFF8FAFC)
val LightOnBackground = Color(0xFF1E293B)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1E293B)
val LightOnSurfaceVariant = Color(0xFF64748B)   // text-muted
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFBFCFE)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightSurfaceContainerHigh = Color(0xFFE9EEF5)
val LightSurfaceContainerHighest = Color(0xFFE2E8F0)
val LightOutline = Color(0xFFCBD5E1)
val LightOutlineVariant = Color(0xFFE7ECF3)
val LightTextStrong = Color(0xFF0F172A)
val LightSuccess = Color(0xFF0D8259)
val LightError = Color(0xFFD92D2D)
val LightAccent = Color(0xFFB4530A)             // community-added

// ---- Dark ----
val DarkPrimary = Color(0xFF7B98FF)             // text / link / active state on dark surfaces
val DarkOnPrimary = Color(0xFF0B1220)
val DarkPrimaryContainer = Color(0xFF1E3A8A)
val DarkOnPrimaryContainer = Color(0xFFDDE6FF)
val DarkBackground = Color(0xFF0F172A)
val DarkOnBackground = Color(0xFFF1F5F9)
val DarkSurface = Color(0xFF0F172A)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)    // text-muted
val DarkSurfaceContainerLowest = Color(0xFF0A101E)
val DarkSurfaceContainerLow = Color(0xFF131E33)
val DarkSurfaceContainer = Color(0xFF17233D)
val DarkSurfaceContainerHigh = Color(0xFF1F2D49)
val DarkSurfaceContainerHighest = Color(0xFF273551)
val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF23324B)
val DarkTextStrong = Color(0xFFF8FAFC)
val DarkSuccess = Color(0xFF34D399)
val DarkError = Color(0xFFF87171)
val DarkAccent = Color(0xFFF0A63E)

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
