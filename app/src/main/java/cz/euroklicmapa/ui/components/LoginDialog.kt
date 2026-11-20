package cz.euroklicmapa.ui.components

import cz.euroklicmapa.util.t
import cz.euroklicmapa.ui.theme.EuroklicTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Shared login-gate dialog — shown wherever a logged-out user taps something that needs an
 * account (More → "Přidat místo", the Map screen's add-place FAB, …). Login itself always
 * happens in the browser via [cz.euroklicmapa.data.auth.AuthRepository.buildLoginUri].
 */
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Přihlásit se")) },
        text = {
            Text(
                t("Přihlášení proběhne v prohlížeči — tam si vyberete Discord nebo Google. ") +
                    t("Používáme ho jen k přidávání a moderaci míst; na procházení mapy není potřeba."),
            )
        },
        confirmButton = {
            Button(shape = EuroklicTheme.shapes.pill, onClick = { onDismiss(); onContinue() }) { Text(t("Pokračovat")) }
        },
        dismissButton = {
            TextButton(shape = EuroklicTheme.shapes.pill, onClick = onDismiss) { Text(t("Zrušit")) }
        },
    )
}
