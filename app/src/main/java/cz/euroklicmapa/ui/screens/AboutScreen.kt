package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.AppStats
import cz.euroklicmapa.ui.viewmodel.StatsViewModel
import cz.euroklicmapa.ui.viewmodel.StatsViewModelFactory
import cz.euroklicmapa.util.appVersionName
import cz.euroklicmapa.util.placesCount

/**
 * The disclaimer + "co je Euroklíč" + NRZP warning used to sit as three cards at the top of
 * "Více", read before anything actionable. One tap away from there now, not in front of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(
        factory = with(LocalContext.current.applicationContext as EuroklicApplication) {
            StatsViewModelFactory(repository)
        },
    ),
) {
    val context = LocalContext.current
    val version = remember(context) { appVersionName(context) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("O projektu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Disclaimer()
            stats?.let { StatsCard(it) }
            InfoCard(
                "Euroklíč je univerzální klíč k bezbariérovým toaletám, výtahům a schodišťovým " +
                    "plošinám po celé ČR. Mají na něj nárok lidé s průkazem TP/ZTP/ZTP-P, " +
                    "se stomií, diabetem, Crohnovou chorobou a další. Vydává ho NRZP ČR.",
            )
            InfoCard(
                "Pozor: NRZP ČR od července 2026 dočasně pozastavila výdej nových klíčů kvůli " +
                    "chybějícímu financování. Aktuální stav ověřujte na webu.",
                warning = true,
            )
            Text(
                "Euroklíč Mapa $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

/**
 * Live counts over the Room cache — the same numbers the website shows in its stats section.
 * Badge semantics mirror the web: "ověřeno" = 👍, "nahlášeno" = 👎 převažuje.
 */
@Composable
private fun StatsCard(stats: AppStats) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Statistiky",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    append("Bezbariérových WC: ")
                    append(placesCount(stats.totalWc))
                    if (stats.verifiedWc > 0 || stats.reportedWc > 0) {
                        append(" (")
                        if (stats.verifiedWc > 0) append("${stats.verifiedWc} ověřeno")
                        if (stats.verifiedWc > 0 && stats.reportedWc > 0) append(", ")
                        if (stats.reportedWc > 0) append("${stats.reportedWc} nahlášeno")
                        append(")")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Výdejních míst klíče: ${stats.totalPickup}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats.topLiked?.let {
                Text(
                    "Nejlépe hodnocené: ${it.title} (+${it.likes} 👍)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            stats.topDisliked?.let {
                Text(
                    "Nejvíce problémové: ${it.title} (${it.dislikes} 👎)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Disclaimer() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Nezávislý projekt",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Data agregujeme z veřejných zdrojů. Nejsme oficiální aplikace systému Euroklíč " +
                    "ani NRZP ČR.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String, warning: Boolean = false) {
    Surface(
        color = if (warning) EuroklicTheme.extended.accent.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
