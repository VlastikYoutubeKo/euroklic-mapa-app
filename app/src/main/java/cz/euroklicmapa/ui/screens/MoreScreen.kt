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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val loggedIn = authState is AuthState.LoggedIn
    val isAdmin = (authState as? AuthState.LoggedIn)?.me?.is_admin == true

    val version = remember(context) { appVersionName(context) }

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
}

@Composable
private fun AuthCard(
    state: AuthState,
    onRequestLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
