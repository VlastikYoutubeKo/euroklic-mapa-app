package cz.euroklicmapa.ui.components

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import cz.euroklicmapa.ui.theme.EuroklicTheme

/**
 * `FilterChip`'s default selected state pulls from `secondaryContainer`, which this app's theme
 * gives its own "community-added" amber semantic (Color.kt) — wrong hue for a generic selection
 * control (found on ListScreen's category/filter chips, 2026-09-24; the exact same mismatch was
 * still lurking in MoreScreen's theme-toggle chips, missed the first time because they used the
 * bare `FilterChip(selected = …)` default with no explicit `colors`, so a text search for
 * "secondaryContainer" in our own code never found them). Chip selection is a brand-blue
 * interaction (matches the FAB/Navigate/brandButton), so every "pick one of these chips" control
 * in the app should use this instead of the implicit default.
 */
@Composable
fun brandChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = EuroklicTheme.extended.brandButton,
    selectedLabelColor = EuroklicTheme.extended.onBrandButton,
    selectedLeadingIconColor = EuroklicTheme.extended.onBrandButton,
)
