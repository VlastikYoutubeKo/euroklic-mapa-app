package cz.euroklicmapa.odjezdy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import cz.euroklicmapa.ui.components.brandChipColors
import cz.euroklicmapa.ui.theme.EuroklicTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.euroklicmapa.EuroklicApplication
import cz.euroklicmapa.data.prefs.AppMode
import cz.euroklicmapa.data.prefs.AppModePrefs
import cz.euroklicmapa.util.openUrl
import cz.euroklicmapa.util.t
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ODTab { MAP, SEARCH, FAVORITES, MORE }

/** Root of the "Odjezdy" (public transport) mode: Map / Search / Favorites / More. */
@Composable
fun OdjezdyRoot(vm: ODViewModel = viewModel()) {
    var tab by remember { mutableStateOf(ODTab.MAP) }
    var center by remember { mutableStateOf<GeoPoint?>(null) }

    fun showOnMap(stop: ODStop) {
        center = GeoPoint(stop.lat, stop.lng)
        vm.selectStop(stop)
        tab = ODTab.MAP
    }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            NavigationBar {
                NavItem(tab == ODTab.MAP, { tab = ODTab.MAP }, Icons.Rounded.DirectionsBus, t("Mapa"))
                NavItem(tab == ODTab.SEARCH, { tab = ODTab.SEARCH }, Icons.Rounded.Search, t("Hledání"))
                NavItem(tab == ODTab.FAVORITES, { tab = ODTab.FAVORITES }, Icons.Filled.Star, t("Oblíbené"))
                NavItem(tab == ODTab.MORE, { tab = ODTab.MORE }, Icons.Rounded.MoreHoriz, t("Více"))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                ODTab.MAP -> ODMapTab(vm, center)
                ODTab.SEARCH -> ODSearchTab(vm, onPick = ::showOnMap)
                ODTab.FAVORITES -> ODFavoritesTab(vm, onPick = ::showOnMap)
                ODTab.MORE -> ODMoreTab()
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = EuroklicTheme.extended.onNavIndicator,
            selectedTextColor = EuroklicTheme.extended.navActive,
            indicatorColor = EuroklicTheme.extended.navIndicator,
            unselectedIconColor = EuroklicTheme.extended.navText,
            unselectedTextColor = EuroklicTheme.extended.navText,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Map
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ODMapTab(vm: ODViewModel, center: GeoPoint?) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.favoriteStops.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as EuroklicApplication
    val myLocation by app.locationRepository.lastKnown.collectAsStateWithLifecycle()
    var recenter by remember { mutableStateOf<GeoPoint?>(null) }

    // Poll vehicles only while this tab is on screen and the app is in the foreground.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.setActive(true)
                Lifecycle.Event.ON_PAUSE -> vm.setActive(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.setActive(true)
        onDispose { owner.lifecycle.removeObserver(observer); vm.setActive(false) }
    }

    Box(Modifier.fillMaxSize()) {
        ODMap(
            state = state,
            center = recenter ?: center,
            myLocation = myLocation,
            onViewport = vm::onViewportChanged,
            onVehicle = vm::selectVehicle,
            onStop = vm::selectStop,
            onMapClick = { vm.selectVehicle(null) },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ODVehicleType.entries) { type ->
                    FilterChip(
                        selected = type in state.filter,
                        onClick = { vm.toggleType(type) },
                        label = { Text(type.label) },
                        colors = brandChipColors(),
                    )
                }
            }
            if (state.zoomedOutTooFar) Hint(t("Přibliž mapu, ať se zobrazí vozidla."))
            else if (state.totalInView > ODViewModel.MAX_MARKERS) Hint(t("Zobrazeno %s z %s vozidel. Přibliž mapu.", ODViewModel.MAX_MARKERS, state.totalInView))
            state.error?.let { Hint(it) }
        }

        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))

        SmallFloatingActionButton(
            onClick = {
                app.locationRepository.refresh { p -> if (p != null) recenter = GeoPoint(p.latitude, p.longitude) }
                myLocation?.let { recenter = GeoPoint(it.latitude, it.longitude) }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = if (state.selectedVehicle != null) 150.dp else 0.dp),
        ) { Icon(Icons.Rounded.MyLocation, contentDescription = t("Moje poloha")) }

        state.selectedVehicle?.let { v ->
            VehicleCard(
                v,
                info = state.info,
                infoPending = state.infoPending,
                onClose = { vm.selectVehicle(null) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.selectedStop?.let { stop ->
        ModalBottomSheet(onDismissRequest = { vm.selectStop(null) }) {
            StopSheet(
                stop = stop,
                departures = state.departures,
                loading = state.departuresLoading,
                error = state.departuresError,
                favorite = favorites.any { it.id == stop.id },
                onToggleFavorite = { vm.toggleFavorite(stop) },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun LinePill(type: String?, line: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color(vehicleColor(type)), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .semantics { contentDescription = "${ODVehicleType.of(type).label} $line" },
        contentAlignment = Alignment.Center,
    ) { Text(line, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1) }
}

@Composable
private fun VehicleCard(
    v: ODVehicle,
    info: ODVehicleInfo?,
    infoPending: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinePill(v.type, v.line.orEmpty())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.destination?.takeIf { it.isNotBlank() } ?: t("Bez cíle"), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(ODVehicleType.of(v.type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = t("Zavřít")) }
            }
            Spacer(Modifier.height(8.dp))
            DelayText(v.delayMinutes)
            v.nextStopName?.let {
                Text(t("Další zastávka: %s", it), style = MaterialTheme.typography.bodyMedium)
            }
            VehicleDetails(v, info, infoPending) { url -> openUrl(context, url) }
        }
    }
}

/** Fleet number, plate, model, operator and equipment — from the map feed, completed by `/api/vehicle-info` when available. */
@Composable
private fun VehicleDetails(v: ODVehicle, info: ODVehicleInfo?, pending: Boolean, onOpenUrl: (String) -> Unit) {
    val rows = buildList {
        (info?.fleetNo?.takeIf { it.isNotBlank() } ?: v.fleetNo)?.let { add(t("Ev. č.") to it) }
        (info?.plate?.takeIf { it.isNotBlank() } ?: v.plate)?.let { add(t("SPZ") to it) }
        val model = listOfNotNull(info?.manufacturer, info?.model?.takeIf { it != info.manufacturer } ?: v.attributes?.model)
            .distinct().joinToString(" ").takeIf { it.isNotBlank() }
        model?.let { add(t("Vůz") to listOfNotNull(it, info?.year).joinToString(" · ")) }
        (info?.carrier ?: v.attributes?.carrier)?.takeIf { it.isNotBlank() }?.let { add(t("Dopravce") to it) }
        info?.depot?.takeIf { it.isNotBlank() }?.let { add(t("Vozovna") to it) }
        info?.status?.takeIf { it.isNotBlank() }?.let { add(t("Stav") to it) }
        info?.equipmentText?.let { add(t("Vybavení") to it) }
        info?.note?.takeIf { it.isNotBlank() }?.let { add(t("Poznámka") to it) }
    }
    val features = listOfNotNull(
        t("Bezbariérový").takeIf { v.attributes?.wheelchair == true },
        t("Wi-Fi").takeIf { v.attributes?.wifi == true },
        t("Klimatizace").takeIf { v.attributes?.ac == true },
    )
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    rows.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (features.isNotEmpty()) {
        Text(features.joinToString(" · "), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pending) {
        Text(t("Údaje o vozidle se dohledávají…"), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    (info?.url ?: v.attributes?.saUrl)?.takeIf { it.startsWith("https://") }?.let { url ->
        TextButton(onClick = { onOpenUrl(url) }) { Text(t("Detail vozu na seznam-autobusu.cz")) }
    }
    TextButton(onClick = { onOpenUrl("https://app.odjezdy.online/#v=" + java.net.URLEncoder.encode(v.id, "UTF-8")) }) {
        Text(t("Otevřít na odjezdy.online"))
    }
}

@Composable
private fun DelayText(minutes: Int?) {
    val (text, color) = when {
        minutes == null -> t("Zpoždění neznámé") to MaterialTheme.colorScheme.onSurfaceVariant
        minutes <= 0 -> t("Jede včas") to Color(0xFF0B7A4B)
        minutes < 5 -> t("Zpoždění %s min", minutes) to MaterialTheme.colorScheme.onSurface
        else -> t("Zpoždění %s min", minutes) to Color(0xFFC4161C)
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
}

// ---------------------------------------------------------------------------------------------
// Departures sheet
// ---------------------------------------------------------------------------------------------

@Composable
private fun StopSheet(
    stop: ODStop,
    departures: List<ODDeparture>,
    loading: Boolean,
    error: String?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (favorite) t("Odebrat z oblíbených") else t("Přidat do oblíbených"),
                    tint = if (favorite) Color(0xFFE0A100) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            departures.isEmpty() -> Text(t("Žádné odjezdy v nejbližších hodinách."))
            else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(departures.take(40), key = { (it.id ?: "") + (it.plannedTime ?: "") + it.line }) { d ->
                    DepartureRow(d)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(d: ODDeparture) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) { LinePill(d.type, d.line) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(d.destination, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
            val extra = listOfNotNull(
                d.platform?.takeIf { it.isNotBlank() }?.let { t("nást. %s", it) },
                if (d.cancelled) t("Zrušeno") else d.delayMinutes?.takeIf { it > 0 }?.let { t("+%s min", it) },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(
                    extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.cancelled || (d.delayMinutes ?: 0) >= 5) Color(0xFFC4161C) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(departureTime(d.whenMillis), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

/** "teď" / "5 min" within the hour, otherwise a clock time. */
private fun departureTime(millis: Long?): String {
    if (millis == null) return "–"
    val minutes = (millis - System.currentTimeMillis()) / 60_000
    return when {
        minutes <= 0 -> t("teď")
        minutes < 60 -> t("%s min", minutes)
        else -> SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(millis))
    }
}

// ---------------------------------------------------------------------------------------------
// Search / favorites / more
// ---------------------------------------------------------------------------------------------

@Composable
private fun ODSearchTab(vm: ODViewModel, onPick: (ODStop) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ODStop>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList(); message = null; return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)
        busy = true
        vm.searchStops(query.trim())
            .onSuccess { results = it; message = if (it.isEmpty()) t("Nic nenalezeno.") else null }
            .onFailure { message = (it as? ODError)?.let(vm::describe) }
        busy = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(t("Hledat zastávku")) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { scope.launch { } }),
        )
        Spacer(Modifier.height(12.dp))
        if (busy) LinearBusy()
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LazyColumn {
            items(results, key = { it.id }) { stop -> StopRow(stop) { onPick(stop) } }
        }
    }
}

@Composable
private fun LinearBusy() {
    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun StopRow(stop: ODStop, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(stop.name, style = MaterialTheme.typography.bodyLarge)
        stop.provider?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ODFavoritesTab(vm: ODViewModel, onPick: (ODStop) -> Unit) {
    val favorites by vm.favoriteStops.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(t("Oblíbené zastávky"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (favorites.isEmpty()) {
            Text(
                t("Zatím nic. V odjezdech zastávky klepni na hvězdičku a uloží se sem."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn { items(favorites, key = { it.id }) { stop -> StopRow(stop) { onPick(stop) } } }
        }
    }
}

@Composable
private fun ODMoreTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Více"), style = MaterialTheme.typography.headlineLarge)
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { scope.launch { AppModePrefs(context).set(AppMode.EUROKLIC) } },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(t("Zpět na Euroklíč Mapu"), style = MaterialTheme.typography.titleMedium)
                Text(t("Bezbariérová WC s Euroklíčem"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { openUrl(context, "https://odjezdy.online") },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("odjezdy.online", style = MaterialTheme.typography.titleMedium)
                Text(t("Web Odjezdy Online: mapa, výluky a další"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            t("Data o poloze vozidel a odjezdech poskytuje odjezdy.online. Časy jsou orientační."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
