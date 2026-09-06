package cz.euroklicmapa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

/** Below this the data is considered current and the banner stays hidden. */
private val FRESH_WINDOW_MS = TimeUnit.HOURS.toMillis(2)

fun dataAgeText(lastSyncMillis: Long?, now: Long = System.currentTimeMillis()): String? {
    if (lastSyncMillis == null) return "Zatím se nepodařilo stáhnout aktuální data"
    val age = now - lastSyncMillis
    if (age < FRESH_WINDOW_MS) return null
    val hours = TimeUnit.MILLISECONDS.toHours(age)
    val days = TimeUnit.MILLISECONDS.toDays(age)
    val ago = when {
        days >= 1L -> "před $days dny"
        hours >= 1L -> "před $hours h"
        else -> "před chvílí"
    }
    return "Offline data · aktualizováno $ago"
}

/** Non-hidden staleness notice. Renders nothing when the cache is current. */
@Composable
fun DataFreshnessBanner(lastSyncMillis: Long?, modifier: Modifier = Modifier) {
    val text = dataAgeText(lastSyncMillis) ?: return
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
