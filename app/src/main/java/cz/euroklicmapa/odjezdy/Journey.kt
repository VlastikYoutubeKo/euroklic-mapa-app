package cz.euroklicmapa.odjezdy

import cz.euroklicmapa.util.formatDistance
import cz.euroklicmapa.util.t
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.ceil
import kotlin.math.max

/** Stop in a connection; the planner used to send walk-leg stops as a plain string, now as an object. */
data class JourneyStop(val name: String, val lat: Double? = null, val lng: Double? = null)

data class JourneyWalk(val meters: Double = 0.0, val seconds: Double = 0.0)

sealed interface JourneyLeg {
    data class Ride(
        val line: String,
        /** Public line label (last 3 digits of a 6-digit JDF code), otherwise the same as [line]. */
        val lineLabel: String,
        val mode: String?,
        val headsign: String,
        val from: JourneyStop,
        val to: JourneyStop,
        val departure: String,
        val arrival: String,
        val stopCount: Int?,
        val agency: String?,
        /** `true` = marked as accessible; `null` = unknown (we never claim "not accessible"). */
        val wheelchairAccessible: Boolean?,
        /** Current delay in seconds, only when the server knows it. */
        val delaySeconds: Double? = null,
        /** Stops of this leg (not sent yet; once sent, the "after leaving stop X" hint appears). */
        val stops: List<JourneyStop> = emptyList(),
    ) : JourneyLeg {
        /** The stop before the exit stop; null when the list does not tell reliably. */
        val stopBeforeExit: String?
            get() {
                val i = stops.indexOfLast { it.name == to.name }
                if (i <= 0) return null
                val name = stops[i - 1].name
                return name.takeIf { it.isNotBlank() && !(it == from.name && i - 1 != 0) }
            }

        /** A STOP button only makes sense in a bus, trolleybus or tram; unknown vehicle = say nothing. */
        val mayNeedStopButton: Boolean get() = mode in setOf("bus", "trolleybus", "tram")
    }

    data class Walk(val from: JourneyStop, val to: JourneyStop, val seconds: Double) : JourneyLeg
}

data class Journey(
    val departure: String,
    val arrival: String,
    val transfers: Int,
    val totalSeconds: Double,
    val walkToStart: JourneyWalk,
    val walkFromEnd: JourneyWalk,
    val legs: List<JourneyLeg>,
)

/** `errorCode` set = no result (HTTP 200 with `error`, either a string or an object with `code`). */
data class JourneyResult(val journeys: List<Journey>, val errorCode: String?, val side: String?)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

private fun parseStop(e: JsonElement?): JourneyStop = when (e) {
    is JsonPrimitive -> JourneyStop(e.contentOrNull.orEmpty())
    is JsonObject -> JourneyStop(e["name"].str().orEmpty(), e["lat"].dbl(), e["lng"].dbl())
    else -> JourneyStop("")
}

private fun parseWalk(e: JsonElement?): JourneyWalk =
    e.obj()?.let { JourneyWalk(it["meters"].dbl() ?: 0.0, it["seconds"].dbl() ?: 0.0) } ?: JourneyWalk()

private fun parseLeg(e: JsonElement): JourneyLeg? {
    val o = e.obj() ?: return null
    val from = parseStop(o["from"])
    val to = parseStop(o["to"])
    if (o["kind"].str() == "walk") return JourneyLeg.Walk(from, to, o["seconds"].dbl() ?: 0.0)
    val line = o["line"].str().orEmpty()
    return JourneyLeg.Ride(
        line = line,
        lineLabel = o["lineLabel"].str()?.takeIf { it.isNotBlank() } ?: line,
        mode = o["mode"].str(),
        headsign = o["headsign"].str().orEmpty(),
        from = from,
        to = to,
        departure = o["departure"].str().orEmpty(),
        arrival = o["arrival"].str().orEmpty(),
        stopCount = o["stopCount"].int(),
        agency = o["agency"].str(),
        wheelchairAccessible = o["wheelchairAccessible"].bool(),
        delaySeconds = o["delaySeconds"].dbl(),
        stops = (o["stops"] as? JsonArray).orEmpty().map { parseStop(it) },
    )
}

private fun parseJourney(e: JsonElement): Journey? {
    val o = e.obj() ?: return null
    return Journey(
        departure = o["departure"].str().orEmpty(),
        arrival = o["arrival"].str().orEmpty(),
        transfers = o["transfers"].int() ?: 0,
        totalSeconds = o["totalSeconds"].dbl() ?: 0.0,
        walkToStart = parseWalk(o["walkToStart"]),
        walkFromEnd = parseWalk(o["walkFromEnd"]),
        // One unreadable leg must not drop the whole connection.
        legs = (o["legs"] as? JsonArray).orEmpty().mapNotNull { parseLeg(it) },
    )
}

/** Tolerant parser of `/api/v2/journey`; throws [ODError.BadData] when the body is not a JSON object. */
fun parseJourneyResponse(body: String): JourneyResult {
    val root = runCatching { lenientJson.parseToJsonElement(body) }.getOrNull().obj() ?: throw ODError.BadData
    val error = root["error"]
    val code = when (error) {
        is JsonPrimitive -> error.contentOrNull
        is JsonObject -> error["code"].str()
        else -> null
    }
    val side = (error as? JsonObject)?.get("side").str() ?: root["side"].str()
    val journeys = (root["journeys"] as? JsonArray).orEmpty().mapNotNull { parseJourney(it) }
    return JourneyResult(journeys, code, side)
}

/** `HH:MM` from the planner. Hours can be >= 24 (after midnight); an older server sent `00:03`. */
object JourneyClock {
    fun minutes(text: String): Int? {
        val parts = text.split(":")
        if (parts.size < 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** Departure and arrival as minutes since midnight of the query day; arrival is always after departure. */
    fun span(departure: String, arrival: String): Pair<Int, Int>? {
        val dep = minutes(departure) ?: return null
        var arr = minutes(arrival) ?: return null
        if (arr < dep) arr += 24 * 60
        return dep to arr
    }

    fun format(minutes: Int): String {
        val day = minutes / (24 * 60)
        val inDay = minutes % (24 * 60)
        val text = "%d:%02d".format(inDay / 60, inDay % 60)
        return if (day > 0) "$text +1" else text
    }

    /** Server time for display ("24:03" -> "0:03 +1"); an unknown shape stays as it is. */
    fun display(text: String): String = minutes(text)?.let { format(it) } ?: text

    fun ceilMinutes(seconds: Double): Int = max(1, ceil(seconds / 60.0).toInt())
}

/** Official Mapy.com URL function (no key). It cannot preselect public transport, only walking. */
fun mapyWalkingUrl(fromLat: Double?, fromLng: Double?, toLat: Double, toLng: Double): String {
    val start = if (fromLat != null && fromLng != null) "start=%.6f,%.6f&".format(java.util.Locale.US, fromLng, fromLat) else ""
    return "https://mapy.com/fnc/v1/route?${start}end=%.6f,%.6f&routeType=foot_fast".format(java.util.Locale.US, toLng, toLat)
}

/** One shared client for the connection planner (its first request of the day can take about a minute). */
val sharedJourneyApi: ODApi by lazy { ODApi() }

/**
 * Plain-sentence instructions step by step. Nothing is made up: the STOP button is mentioned only for bus,
 * trolleybus and tram, and "after leaving stop X" only when the stop list really has X.
 */
fun journeyGuide(journey: Journey): List<String> = buildList {
    val firstStop = journey.legs.filterIsInstance<JourneyLeg.Ride>().firstOrNull()?.from?.name
    if (journey.walkToStart.seconds > 0 && firstStop != null) {
        add(t("Dojděte pěšky k zastávce %s (%s).", firstStop, formatDistance(journey.walkToStart.meters)))
    }
    journey.legs.forEach { leg ->
        when (leg) {
            is JourneyLeg.Ride -> {
                val departure = JourneyClock.display(leg.departure)
                if (leg.headsign.isBlank()) {
                    add(t("Na zastávce %s nastupte do linky %s, odjezd %s.", leg.from.name, leg.lineLabel, departure))
                } else {
                    add(t("Na zastávce %s nastupte do linky %s směr %s, odjezd %s.", leg.from.name, leg.lineLabel, leg.headsign, departure))
                }
                if (leg.mayNeedStopButton) {
                    val previous = leg.stopBeforeExit
                    add(
                        if (previous != null) t("Po odjezdu ze zastávky %s stiskněte tlačítko STOP, pokud je vaše zastávka na znamení.", previous)
                        else t("Je-li vaše výstupní zastávka na znamení, stiskněte před ní včas tlačítko STOP."),
                    )
                }
                val arrival = JourneyClock.display(leg.arrival)
                val count = leg.stopCount
                add(
                    if (count != null && count > 0) t("Vystupte na zastávce %s (%s. zastávka, příjezd %s).", leg.to.name, count.toString(), arrival)
                    else t("Vystupte na zastávce %s, příjezd %s.", leg.to.name, arrival),
                )
            }
            is JourneyLeg.Walk -> if (!(leg.from.name == leg.to.name && leg.seconds <= 90)) {
                add(t("Přejděte pěšky na zastávku %s (%s).", leg.to.name, "~${JourneyClock.ceilMinutes(leg.seconds)} min"))
            }
        }
    }
    if (journey.walkFromEnd.seconds > 0) add(t("Dojděte pěšky k cíli (%s).", formatDistance(journey.walkFromEnd.meters)))
}
