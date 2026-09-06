package cz.euroklicmapa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cz.euroklicmapa.ui.theme.ColorPickup
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.theme.sourceColor
import cz.euroklicmapa.ui.theme.sourceLabel
import cz.euroklicmapa.util.PlaceStatus
import cz.euroklicmapa.util.VoteBadge
import cz.euroklicmapa.util.label
import cz.euroklicmapa.util.voteBadge

/**
 * The map's marker grammar in miniature: a **circle** tinted by `source` for a WC location
 * (hollow when reported, with a light ring when verified), a slate **square** for a pickup point.
 * Shape carries the meaning, not colour alone.
 */
@Composable
fun SourceGlyph(
    source: String?,
    isPickup: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 12,
    status: PlaceStatus? = null,
) {
    if (isPickup) {
        Box(modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(ColorPickup))
        return
    }
    val color: Color = sourceColor(source)
    when {
        status?.isHollow == true -> Box(
            modifier
                .size(size.dp)
                .border(2.dp, color.copy(alpha = 0.7f), CircleShape),
        )
        status?.hasRing == true -> Box(
            modifier
                .size((size + 4).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(size.dp).clip(CircleShape).background(color))
        }
        else -> Box(modifier.size(size.dp).clip(CircleShape).background(color))
    }
}

/** Pill: glyph + source label, for cards and the detail screen. */
@Composable
fun SourceBadge(
    source: String?,
    isPickup: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = if (isPickup) "Výdejní místo klíče" else sourceLabel(source)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(999.dp))
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
            .clearAndSetSemantics { contentDescription = "Zdroj: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceGlyph(source, isPickup, size = 10)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

/**
 * Community-trust badge — 1:1 with the website popup badge (`likes`/`dislikes`/`isCd` only, not
 * `last_verified`). Renders nothing when there's no signal. The "Oficiální / Komunitní zdroj"
 * pill beside it carries the origin, so this must not repeat it.
 */
@Composable
fun StatusBadge(
    likes: Int,
    dislikes: Int,
    isCd: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val badge = voteBadge(likes, dislikes, isCd)
    if (badge == VoteBadge.NONE) return

    val ok = EuroklicTheme.extended.success
    val warn = EuroklicTheme.extended.accent
    val neutralFg = MaterialTheme.colorScheme.onSurfaceVariant
    val neutralBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val (fg, bg, glyph) = when (badge) {
        VoteBadge.REPORTED -> Triple(warn, warn.copy(alpha = 0.16f), "⚠")
        VoteBadge.VERIFIED -> Triple(ok, ok.copy(alpha = 0.14f), "✓")
        VoteBadge.CD_NO_VOTES, VoteBadge.NONE -> Triple(neutralFg, neutralBg, "")
    }
    val fullLabel = badge.label(likes, dislikes)
    val text = if (compact) when (badge) {
        VoteBadge.REPORTED -> "Nahlášeno"
        VoteBadge.VERIFIED -> "Ověřeno"
        VoteBadge.CD_NO_VOTES, VoteBadge.NONE -> "Bez hlasů"
    } else fullLabel

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = "Stav: $fullLabel" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph.isNotEmpty()) {
            Text(glyph, style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.padding(end = 5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
