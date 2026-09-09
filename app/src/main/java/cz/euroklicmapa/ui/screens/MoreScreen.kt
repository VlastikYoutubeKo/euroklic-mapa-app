package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.data.auth.AuthState
import cz.euroklicmapa.data.prefs.ThemeMode
import cz.euroklicmapa.ui.components.LoginDialog
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.util.appVersionName
import cz.euroklicmapa.util.openUrl
import kotlinx.coroutines.launch

private const val URL_HOW = "https://euroklic.odjezdy.online/clanky/jak-vybavit-euroklic/"
private const val URL_SITE = "https://euroklic.odjezdy.online/"

/**
 * Kept deliberately short — this used to stack the full disclaimer + "co je Euroklíč" +
 * NRZP warning as three always-visible cards before any actionable content. That's now one tap
 * away via [AboutScreen]; everything here is either a setting or a destination.
 */
@Composable
fun MoreScreen(
    onAddPlace: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as EuroklicApplication
    val scope = rememberCoroutineScope()
    val themeMode by app.themeRepository.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

    val authState by app.authRepository.state.collectAsStateWithLifecycle()
    var showLoginDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val loggedIn = authState is AuthState.LoggedIn
    val isAdmin = (authState as? AuthState.LoggedIn)?.me?.is_admin == true

    val version = remember(context) { appVersionName(context) }

    val nearbyNotif by app.notificationPrefs.nearbyEnabled.collectAsStateWithLifecycle(initialValue = false)
    val adminNotif by app.notificationPrefs.adminQueueEnabled.collectAsStateWithLifecycle(initialValue = true)
    // POST_NOTIFICATIONS is requested lazily, only when a toggle is switched ON (API 33+).
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — the toggle keeps its state either way */ }
    fun maybeAskNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Více",
                style = MaterialTheme.typography.displaySmall,
                color = EuroklicTheme.extended.textStrong,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AuthCard(
                state = authState,
                onRequestLogin = { showLoginDialog = true },
                onLogout = { app.authRepository.logout() },
                onDeleteAccount = { showDeleteDialog = true },
            )

            SectionLabel("Vzhled")
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { scope.launch { app.themeRepository.set(mode) } },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Podle systému"
                                    ThemeMode.LIGHT -> "Světlý"
                                    ThemeMode.DARK -> "Tmavý"
                                },
                            )
                        },
                    )
                }
            }

            SectionLabel("Notifikace")
            SwitchRow(
                title = "Nové místo v okolí",
                subtitle = "Občas (max 1×/den) upozornění na nově přidané bezbariérové WC blízko tebe",
                checked = nearbyNotif,
                onCheckedChange = { on ->
                    scope.launch { app.notificationPrefs.setNearbyEnabled(on) }
                    if (on) maybeAskNotifPermission()
                },
            )
            if (isAdmin) {
                SwitchRow(
                    title = "Fronta ke schválení",
                    subtitle = "Když čeká nové místo nebo návrh fotky (kontrola každých 15 min)",
                    checked = adminNotif,
                    onCheckedChange = { on ->
                        scope.launch { app.notificationPrefs.setAdminQueueEnabled(on) }
                        if (on) maybeAskNotifPermission()
                    },
                )
            }
            BatteryNote(
                onOpenSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )

            if (isAdmin) {
                SectionLabel("Moderace")
                LinkRow("Ke schválení", "Fronta nových míst od uživatelů") { onOpenAdmin() }
            }

            SectionLabel("Přispět")
            LinkRow(
                "Přidat místo",
                if (loggedIn) "Bezbariérové WC s Euroklíčem" else "Nejdřív se přihlaste v kartě nahoře",
            ) {
                if (loggedIn) onAddPlace() else showLoginDialog = true
            }

            SectionLabel("Odkazy")
            LinkRow("Jak získat Euroklíč", "Postup a aktuální stav výdeje · NRZP ČR") { openUrl(context, URL_HOW) }
            LinkRow("euroklic.odjezdy.online", "Web, zdroje dat, nahlášení problému") { openUrl(context, URL_SITE) }
            LinkRow("O projektu", "Nezávislý projekt · verze $version") { onOpenAbout() }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onContinue = { openUrl(context, app.authRepository.buildLoginUri().toString()) },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Smazat účet a data?") },
            text = {
                Text(
                    "Odhlásíme tě a server smaže tvůj účet. Tvé jméno se odpojí od míst a fotek, " +
                        "které jsi přidal — samotné příspěvky na mapě zůstanou. Tuto akci nelze vzít zpět.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        scope.launch {
                            deleting = true
                            val done = app.authRepository.deleteAccount()
                            deleting = false
                            if (done) showDeleteDialog = false
                        }
                    },
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = "Mažu účet" },
                        )
                    } else {
                        Text("Smazat účet", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) { Text("Zrušit") }
            },
        )
    }
}

@Composable
private fun AuthCard(
    state: AuthState,
    onRequestLogin: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is AuthState.Loading -> {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        "Načítám…",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is AuthState.LoggedOut -> {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nepřihlášen",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Přihlaste se pro přidávání míst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onRequestLogin) { Text("Přihlásit se") }
                }

                is AuthState.LoggedIn -> {
                    val me = state.me
                    val avatar = me.avatar_url
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = EuroklicTheme.extended.brandButton,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            me.username?.takeIf { it.isNotBlank() } ?: "Přihlášen",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (me.is_admin) "Přihlášen · administrátor" else "Přihlášen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onLogout) { Text("Odhlásit") }
                }
            }
        }

        if (state is AuthState.LoggedIn) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDeleteAccount) {
                    Text("Smazat účet a data", color = MaterialTheme.colorScheme.error)
                }
            }
        }
      }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                // Whole row is the 48dp+ touch target; the Text pair is the accessible label.
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun BatteryNote(onOpenSettings: () -> Unit) {
    Surface(
        onClick = onOpenSettings,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        // Clickable Surface carries no role by default — announce it as a button; the
        // clickable already merges the title + body into one focus stop.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { role = Role.Button },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Notifikace nechodí?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Některé telefony (Honor, Xiaomi, Samsung) zastavují aplikace na pozadí. " +
                    "Klepnutím otevřeš nastavení aplikace — vypni tam pro Euroklíč Mapa optimalizaci baterie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
