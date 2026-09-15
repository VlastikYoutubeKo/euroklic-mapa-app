package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.viewmodel.AppStats
import cz.euroklicmapa.ui.viewmodel.StatsViewModel
import cz.euroklicmapa.ui.viewmodel.StatsViewModelFactory
import cz.euroklicmapa.util.appVersionName
import cz.euroklicmapa.util.openUrl
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
            LinkRow(
                title = "Sdílet mapu",
                subtitle = "Web má mapu k vložení na jiné stránky — otevřít v prohlížeči",
                onClick = { openUrl(context, "https://euroklic.odjezdy.online/") },
            )
            FaqSection()
            SupportSection(context)
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

/** Same row style as [MoreScreen]'s link rows — title + subtitle + chevron, whole row tappable. */
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

/** Mirrors the web's "Často kladené dotazy (FAQ)" block on `/o-projektu.php`. */
@Composable
private fun FaqSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Často kladené dotazy",
            style = MaterialTheme.typography.titleSmall,
            color = EuroklicTheme.extended.textStrong,
        )
        FAQ_ITEMS.forEach { (question, answer) ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        question,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val FAQ_ITEMS = listOf(
    "Co je to Euroklíč a kdo na něj má nárok?" to
        "Euroklíč je speciální univerzální klíč, kterým lze odemknout bezbariérová WC, plošiny a " +
        "výtahy nejen v ČR, ale i po celé Evropě. Je určen lidem s průkazem TP, ZTP a ZTP/P, " +
        "diabetikům, stomikům, onkologickým pacientům, lidem s roztroušenou sklerózou a rodičům " +
        "dětí do tří let.",
    "Odkud berete data?" to
        "Data o lokacích získáváme primárně jako kompilaci otevřených dat a od našich uživatelů. " +
        "Databáze je otevřená komunitě — každý uživatel může přes interaktivní mapu nahlásit " +
        "nefunkční WC, nebo naopak přidat nové místo.",
    "Můžu si mapu stáhnout do mobilu?" to
        "Ano — a tahle appka je přesně to. Funguje i offline nad staženými daty.",
    "Jak mohu pomoci?" to
        "Nejvíce pomůžete tím, že budete databázi udržovat aktuální. Pokud narazíte na Eurozámek, " +
        "který na mapě chybí, přidejte ho. Pokud dorazíte k toaletě, která je trvale zamčená nebo " +
        "nefunkční, přidejte komentář a upozorněte ostatní.",
)

/** Mirrors the web's donation block ("Chcete projekt podpořit i finančně?") on `/o-projektu.php`. */
@Composable
private fun SupportSection(context: android.content.Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Podpořit projekt",
            style = MaterialTheme.typography.titleSmall,
            color = EuroklicTheme.extended.textStrong,
        )
        Text(
            "Projekt běží ve volném čase a z vlastní kapsy. Pokud vám pomohl, budu rád za podporu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinkRow(
            title = "Donatr.ee",
            subtitle = "Vše na jednom místě",
            onClick = { openUrl(context, "https://donatr.ee/mxnticek") },
        )
        LinkRow(
            title = "PayPal",
            subtitle = "paypal.me/mxnticek",
            onClick = { openUrl(context, "https://paypal.me/mxnticek") },
        )
        LinkRow(
            title = "Revolut",
            subtitle = "revolut.me/vlastiwazs",
            onClick = { openUrl(context, "https://revolut.me/vlastiwazs") },
        )
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
