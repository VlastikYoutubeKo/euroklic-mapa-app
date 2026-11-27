package cz.euroklicmapa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.euroklicmapa.odjezdy.Journey
import cz.euroklicmapa.odjezdy.JourneyClock
import cz.euroklicmapa.odjezdy.JourneyLeg
import cz.euroklicmapa.odjezdy.ODError
import cz.euroklicmapa.odjezdy.mapyWalkingUrl
import cz.euroklicmapa.odjezdy.sharedJourneyApi
import cz.euroklicmapa.ui.theme.EuroklicTheme
import cz.euroklicmapa.ui.theme.contrastBorder
import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.openUrl
import cz.euroklicmapa.util.t
import kotlinx.coroutines.CancellationException
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface JourneyState {
    data object Loading : JourneyState
    data class Loaded(val journeys: List<Journey>) : JourneyState
    data class Message(val text: String, val canRetry: Boolean) : JourneyState
}

private suspend fun loadJourneys(origin: GeoPoint?, target: GeoPoint): JourneyState {
    if (origin == null) {
        return JourneyState.Message(t("Pro hledání spojení zapněte polohu, ať víme, odkud vyrážíte."), false)
    }
    val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
    val api = sharedJourneyApi
    return try {
        var result = api.journey(origin.latitude, origin.longitude, target.latitude, target.longitude, time)
        // Outside built-up areas the nearest stop is often farther than the default 800 m: widen the search once.
        if (result.errorCode == "NO_STOPS_NEARBY") {
            result = api.journey(origin.latitude, origin.longitude, target.latitude, target.longitude, time, radius = 2000)
        }
        when (result.errorCode) {
            null -> if (result.journeys.isEmpty()) {
                JourneyState.Message(t("Spojení se nepodařilo najít."), true)
            } else JourneyState.Loaded(result.journeys)
            "NO_STOPS_NEARBY" -> JourneyState.Message(
                if (result.side == "to") t("V okolí místa není do 2 km žádná zastávka.")
                else t("V okolí vaší polohy není do 2 km žádná zastávka."),
                false,
            )
            "SCHEDULE_NOT_AVAILABLE" -> JourneyState.Message(t("Jízdní řády na tento den ještě nejsou k dispozici."), false)
            "NO_SERVICE" -> JourneyState.Message(t("Dnes nejsou k dispozici žádné spoje."), false)
            else -> JourneyState.Message(t("Spojení se nepodařilo najít."), true)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ODError) {
        JourneyState.Message(
            when (e) {
                ODError.Offline -> t("Jste offline. Zkontrolujte připojení k internetu.")
                is ODError.RateLimited -> t("Server odjezdy.online je dočasně přetížený. Zkusíme to za chvilku.")
                is ODError.Server, ODError.BadData -> t("Spojení se nepodařilo načíst.")
            },
            true,
        )
    }
}

/** Connection from the user's location to the place: walk to a stop, ride, walk to the place (planner of odjezdy.online). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneySheet(
    target: GeoPoint,
    targetName: String?,
    origin: GeoPoint?,
    onOpenMaps: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<JourneyState>(JourneyState.Loading) }
    LaunchedEffect(attempt) {
        state = JourneyState.Loading
        state = loadJourneys(origin, target)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t("Spojení MHD"), style = MaterialTheme.typography.titleLarge)
            if (!targetName.isNullOrBlank()) {
                Text(t("Cíl: %s", targetName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (val s = state) {
                JourneyState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        .semantics { contentDescription = t("Hledám spojení…") },
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text(t("Hledám spojení…"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        t("První hledání může trvat až minutu."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is JourneyState.Loaded -> s.journeys.forEach { JourneyCard(it) }
                is JourneyState.Message -> InfoBox {
                    Text(s.text, style = MaterialTheme.typography.bodyMedium)
                    if (s.canRetry) TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Zkusit znovu")) }
                }
            }
            Text(
                t("Chůze je odhad vzdušnou čarou, bezbariérovost cesty ani zastávek není ověřená. Jízdní řády: odjezdy.online."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenMaps, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Otevřít v Mapách")) }
            OutlinedButton(
                onClick = {
                    openUrl(context, mapyWalkingUrl(origin?.latitude, origin?.longitude, target.latitude, target.longitude))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(t("Trasa pěšky v Mapy.com")) }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun InfoBox(content: @Composable () -> Unit) {
    Surface(
        shape = EuroklicTheme.shapes.card,
        color = EuroklicTheme.extended.cardSurface,
        border = contrastBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
private fun JourneyCard(journey: Journey) {
    val span = JourneyClock.span(journey.departure, journey.arrival)
    val leave = span?.let { JourneyClock.format(it.first - JourneyClock.ceilMinutes(journey.walkToStart.seconds)) }
    val arrive = span?.let {
        JourneyClock.format(it.second + if (journey.walkFromEnd.seconds > 0) JourneyClock.ceilMinutes(journey.walkFromEnd.seconds) else 0)
    }
    val transfers = if (journey.transfers == 0) t("Bez přestupu") else t("Přestupů: %s", journey.transfers.toString())
    val rides = journey.legs.filterIsInstance<JourneyLeg.Ride>()
    InfoBox {
        if (leave != null && arrive != null) {
            Text(t("Vyjděte v %s", leave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                t("Na místě v %s", arrive) + " · " + transfers,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(transfers, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val firstStop = rides.firstOrNull()?.from?.name
        if (journey.walkToStart.seconds > 0 && firstStop != null) {
            WalkRow(t("Pěšky k zastávce %s", firstStop), walkDetail(journey.walkToStart.meters, journey.walkToStart.seconds))
        }
        journey.legs.forEach { leg ->
            when (leg) {
                is JourneyLeg.Ride -> RideRow(leg)
                is JourneyLeg.Walk -> {
                    // A transfer within one stop ("Muzeum → Muzeum", ~1 min) says nothing.
                    if (!(leg.from.name == leg.to.name && leg.seconds <= 90)) {
                        WalkRow(
                            t("Přestup pěšky"),
                            "${leg.from.name} → ${leg.to.name} · ~${JourneyClock.ceilMinutes(leg.seconds)} min",
                        )
                    }
                }
            }
        }
        val lastStop = rides.lastOrNull()?.to?.name
        if (journey.walkFromEnd.seconds > 0 && lastStop != null) {
            WalkRow(t("Pěšky od zastávky %s k místu", lastStop), walkDetail(journey.walkFromEnd.meters, journey.walkFromEnd.seconds))
        }
    }
}

private fun walkDetail(meters: Double, seconds: Double) = "${formatDistance(meters)} · ~${JourneyClock.ceilMinutes(seconds)} min"

@Composable
private fun WalkRow(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RideRow(ride: JourneyLeg.Ride) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineBadge(ride.lineLabel, ride.mode)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (ride.headsign.isNotBlank()) Text(ride.headsign, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${ride.from.name} ${ride.departure} → ${ride.to.name} ${ride.arrival}", style = MaterialTheme.typography.bodySmall)
            ride.stopCount?.let {
                Text(t("Zastávek: %s", it.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ride.line != ride.lineLabel) {
                Text(t("Číslo linky: %s", ride.line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!ride.agency.isNullOrBlank()) {
                Text(ride.agency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ride.wheelchairAccessible == true) {
                Text(t("Označeno jako bezbariérové"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Line badge in the Odjezdy style: square with a small radius, colour by vehicle type. */
@Composable
private fun LineBadge(label: String, mode: String?) {
    val color = when (mode) {
        "train" -> Color(0xFF0B7A4B)
        "tram" -> Color(0xFFC4161C)
        "metro" -> Color(0xFF16181D)
        "bus", "trolleybus", "ferry" -> Color(0xFF1D5FD1)
        else -> Color(0xFF5C6470)
    }
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = t("Linka %s", label) },
    )
}
