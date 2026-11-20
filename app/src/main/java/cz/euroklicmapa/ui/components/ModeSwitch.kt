package cz.euroklicmapa.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cz.euroklicmapa.data.prefs.AppMode
import cz.euroklicmapa.data.prefs.AppModePrefs
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.util.t
import kotlinx.coroutines.launch

/**
 * One-tap switch between the two halves of the app (Euroklíč toilets ⇄ Odjezdy public transport).
 * Shown on both maps so the user never has to dig through "Více" — the choice is persisted by
 * [AppModePrefs] and `MainActivity` swaps the root.
 *
 * @param target the mode the pill switches **to** (so on the Euroklíč map pass [AppMode.ODJEZDY]).
 */
@Composable
fun ModeSwitchPill(target: AppMode, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = if (target == AppMode.ODJEZDY) t("Odjezdy") else t("Toalety")
    val icon = if (target == AppMode.ODJEZDY) Icons.Rounded.DirectionsBus else Icons.Rounded.Wc
    val description = if (target == AppMode.ODJEZDY) t("Přepnout na režim Odjezdy") else t("Zpět na Euroklíč Mapu")
    Surface(
        shape = EuroklicTheme.shapes.pill,
        color = EuroklicTheme.extended.brandButton,
        contentColor = EuroklicTheme.extended.onBrandButton,
        shadowElevation = 3.dp,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) { scope.launch { AppModePrefs(context).set(target) } },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
