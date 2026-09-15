package cz.euroklicmapa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.euroklicmapa.ui.theme.EuroklicTheme

/**
 * Static content mirroring the web's `/clanky/` articles — there is no API for these (confirmed:
 * PHP-rendered static pages, not DB-backed), so they're hardcoded here rather than fetched.
 * Web source: https://euroklic.odjezdy.online/clanky/{slug}/
 */
data class Article(
    val slug: String,
    val emoji: String,
    val title: String,
    val intro: String,
    val sections: List<Pair<String, String>>,
    val warning: String? = null,
)

object Articles {
    val all = listOf(
        Article(
            slug = "jak-vybavit-euroklic",
            emoji = "📖",
            title = "Jak získat Euroklíč",
            intro = "Euroklíč je univerzální klíč, který umožňuje osobám se sníženou schopností pohybu " +
                "a orientace přístup k bezbariérovým veřejným toaletám, výtahům a plošinám osazeným " +
                "jednotným Eurozámkem po celé Evropě. Výhradním distributorem v ČR je Národní rada " +
                "osob se zdravotním postižením ČR (NRZP ČR).",
            warning = "Aktuální stav (od července 2026): NRZP ČR dočasně pozastavila výdej nových " +
                "Euroklíčů – distribuční místa nemají klíče skladem a Ministerstvo pro místní rozvoj " +
                "zatím nevypsalo dotační program na jejich nákup. Do té doby zkuste kontaktovat přímo " +
                "NRZP (nrzpcr@nrzp.cz, 230 234 954), nebo se zeptat na obecním úřadě či u neziskových " +
                "organizací pro osoby se zdravotním postižením ve vašem kraji. Postup níže popisuje, " +
                "jak výdej funguje běžně — jen počítejte s tím, že teď může váznout.",
            sections = listOf(
                "1. Kdo má nárok na Euroklíč zdarma?" to
                    "Euroklíč lze ve většině krajů ČR získat zcela zdarma. Nárok mají: držitelé " +
                    "průkazu TP, ZTP nebo ZTP/P; diabetici, stomici, onkologičtí pacienti, osoby s " +
                    "roztroušenou sklerózou, Parkinsonovou chorobou, cystickou fibrózou, Crohnovou " +
                    "chorobou, ulcerózní kolitidou a močovými dysfunkcemi; osoby s autismem. Rodiče " +
                    "dětí do 3 let věku si mohou Euroklíč zapůjčit přes organizaci Síť pro rodinu — " +
                    "po dosažení 3 let věku dítěte je nutné klíč vrátit.",
                "2. Jaké dokumenty potřebuji?" to
                    "Občanský průkaz (k prokázání totožnosti) a průkaz OZP (TP/ZTP/ZTP-P) nebo WC " +
                    "kartu. Pokud tyto průkazy nemáte, postačí k nahlédnutí lékařská zpráva dokládající " +
                    "diagnózu — kvůli GDPR se přesná diagnóza do systému nezapisuje, pouze podepíšete " +
                    "čestné prohlášení.",
                "3. Kde si mohu klíč vyzvednout?" to
                    "Klíče se vydávají na krajských distribučních místech. Seznam všech aktuálních míst " +
                    "najdete na oficiálním webu euroklic.cz v sekci „Kraje“. Před osobní návštěvou vždy " +
                    "předem zavolejte nebo napište e-mail, ať zbytečně nejezdíte, pokud by pracoviště " +
                    "mělo klíče zrovna vyprodané.",
                "4. Co dělat při ztrátě klíče?" to
                    "Euroklíč je přísně nepřenosný. Pokud klíč ztratíte, nebo pokud nesplňujete " +
                    "podmínky pro vydání zdarma (např. senior bez ZTP), můžete si ho zakoupit — cena " +
                    "se pohybuje obvykle kolem 400 Kč přímo přes NRZP ČR.",
            ),
        ),
        Article(
            slug = "kde-plati-euroklic",
            emoji = "🌍",
            title = "Kde všude platí Euroklíč",
            intro = "Euroklíč je skvělý pomocník, který vám otevírá dveře (doslova) k bezbariérovým " +
                "zařízením. Kde všude s ním ale pochodíte?",
            sections = listOf(
                "V České republice" to
                    "V ČR je systém Euroklíče velmi dobře zavedený — klíčem odemknete přes tisíc míst. " +
                    "Nejčastěji na dálničních odpočívadlech a benzínových pumpách (MOL, Benzina/Orlen, " +
                    "Shell), na vlakových a autobusových nádražích (téměř všechna zrekonstruovaná " +
                    "nádraží ČD), ve veřejných budovách (úřady, knihovny, magistráty), v obchodních " +
                    "centrech a v nemocnicích a poliklinikách (často i speciální výtahy).",
                "Na Slovensku" to
                    "Slovensko se k projektu také připojilo a počet míst neustále roste. Nejčastěji ho " +
                    "využijete na novějších dálničních odpočívadlech (např. Slovnaft) a ve větších " +
                    "obchodních centrech ve městech jako Bratislava, Košice nebo Žilina. Přesná místa " +
                    "najdete vždy na Euroklíč Mapě.",
                "Ve zbytku Evropy" to
                    "Euroklíč (původním názvem Euroschlüssel) vznikl v Německu v roce 1986, a je proto " +
                    "nejlépe pokrytou zemí. Německo má téměř 100% pokrytí na dálnicích (sanitární " +
                    "zařízení Sanifair). Rakousko a Švýcarsko mají špičkové pokrytí na odpočívadlech " +
                    "(např. ASFINAG) a turistických atrakcích. Klíč dál funguje ve Francii, Itálii, " +
                    "Nizozemsku, Velké Británii a dalších zemích západní Evropy.",
            ),
        ),
    )

    fun bySlug(slug: String): Article? = all.find { it.slug == slug }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(onOpenArticle: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Články a návody") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Articles.all.forEach { article ->
                Surface(
                    onClick = { onOpenArticle(article.slug) },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(article.emoji, style = MaterialTheme.typography.headlineSmall)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                article.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                article.intro,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(slug: String, onBack: () -> Unit) {
    val article = Articles.bySlug(slug)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "Článek") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (article == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${article.emoji} ${article.title}",
                style = MaterialTheme.typography.headlineSmall,
                color = EuroklicTheme.extended.textStrong,
            )
            Text(
                article.intro,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            article.warning?.let { ArticleWarning(it) }
            article.sections.forEach { (heading, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleMedium,
                        color = EuroklicTheme.extended.textStrong,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleWarning(text: String) {
    Surface(
        color = EuroklicTheme.extended.accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "⚠️ $text",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
