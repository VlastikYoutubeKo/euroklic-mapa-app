package cz.euroklicmapa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.euroklicmapa.util.ChangelogEntry
import cz.euroklicmapa.util.t

/**
 * Release notes: one entry right after an update, or the whole history (More → What's new).
 * Text comes from the bundled changelog, in the phone's language (Czech or English).
 */
@Composable
fun ChangelogDialog(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Co je nového")) },
        text = {
            if (entries.isEmpty()) {
                Text(t("Seznam změn není v této verzi k dispozici."))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(entries, key = { it.version }) { entry ->
                        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.padding(top = 6.dp))
                            entry.items.forEach { item ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                    Text(item, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Hotovo")) } },
    )
}
