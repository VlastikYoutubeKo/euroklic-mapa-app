package cz.euroklicmapa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.theme.sourceColor
import cz.euroklicmapa.ui.theme.sourceLabel
import cz.euroklicmapa.util.PlaceStatus
import cz.euroklicmapa.util.VoteBadge
import cz.euroklicmapa.util.countryName
import cz.euroklicmapa.util.isForeignCountry
import cz.euroklicmapa.util.label
import cz.euroklicmapa.util.voteBadge

/**
 * One row in the list / one hit on the map sheet. Serves "which of these is nearest?": the
 * distance is the loudest element, the name second, everything else quiet.
 */
@Composable
fun PlaceCard(
    title: String,
    distance: String?,
    walk: String?,
    source: String?,
    isPickup: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: PlaceStatus? = null,
    country: String? = null,
    likes: Int = 0,
    dislikes: Int = 0,
) {
    val sourceText = if (isPickup) "Výdejní místo klíče" else sourceLabel(source)
    val secondary = subtitle?.takeIf { it.isNotBlank() } ?: sourceText
    val vb = if (isPickup) VoteBadge.NONE else voteBadge(likes, dislikes, source == "cd")
    val showStatus = vb != VoteBadge.NONE
    val foreign = isForeignCountry(country)
    val a11y = buildString {
        append(title)
        distance?.let { append(", $it") }
        walk?.let { append(", $it pěšky") }
        append(", $sourceText")
        if (showStatus) append(", ${vb.label(likes, dislikes)}")
        if (foreign) append(", ${countryName(country)}")
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val glyphTint = if (isPickup) cz.euroklicmapa.ui.theme.ColorPickup else sourceColor(source)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(glyphTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                SourceGlyph(source = source, isPickup = isPickup, size = 16, status = status)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = EuroklicTheme.extended.textStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showStatus) {
                        StatusBadge(
                            likes = likes,
                            dislikes = dislikes,
                            isCd = source == "cd",
                            compact = true,
                        )
                    }
                    if (foreign) {
                        Text(
                            text = countryName(country),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (distance != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    if (walk != null) {
                        Text(
                            text = walk,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

