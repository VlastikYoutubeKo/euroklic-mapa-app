package cz.euroklicmapa.odjezdy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Models for https://app.odjezdy.online (read-only public API; see docs/odjezdy-api-for-ios.md).
// Every field is optional on purpose: a single missing key must not fail a 4 MB vehicle list.

@Serializable
data class ODLocation(val lat: Double = 0.0, val lng: Double = 0.0, val bearing: Double? = null)

@Serializable
data class ODDelay(val minutes: Int? = null, val offline: Boolean? = null)

/** Extra per-vehicle data in the map feed. `evc` is the fleet number for some operators; PID puts it in `spz`. */
@Serializable
data class ODAttributes(
    val evc: String? = null,
    val spz: String? = null,
    val wheelchair: Boolean? = null,
    val wifi: Boolean? = null,
    val ac: Boolean? = null,
    val model: String? = null,
    val carrier: String? = null,
    val saUrl: String? = null,
)

@Serializable
data class ODVehicle(
    val id: String,
    val provider: String? = null,
    val type: String = "bus",
    val line: String? = null,
    val destination: String? = null,
    val location: ODLocation = ODLocation(),
    val currentStop: JsonElement? = null,
    val nextStop: JsonElement? = null,
    val delay: ODDelay? = null,
    val attributes: ODAttributes? = null,
) {
    /** Fleet number (ev. č.): `evc` where the operator reports it, for PID the "spz" field holds it (confirmed by the backend). */
    val fleetNo: String?
        get() = attributes?.evc?.takeIf { it.isNotBlank() }
            ?: attributes?.spz?.takeIf { provider == "PID" || it.matches(Regex("""\d{1,6}""")) }

    /** Licence plate when `spz` really is one (not just the fleet number). */
    val plate: String?
        get() = attributes?.spz?.takeIf { it.isNotBlank() && provider != "PID" && !it.matches(Regex("""\d{1,6}""")) }

    /** `"N/A"` or blank means the vehicle is out of service / in the depot. */
    val inService: Boolean get() = !line.isNullOrBlank() && !line.equals("N/A", ignoreCase = true)
    val delayMinutes: Int? get() = delay?.minutes
    val nextStopName: String? get() = nextStop.stopName()
}

/** `nextStop` / `currentStop` arrive either as a plain string or as an object with a `name`. */
private fun JsonElement?.stopName(): String? = when (this) {
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    is JsonObject -> (this["name"] as? JsonPrimitive)?.contentOrNull
    else -> null
}

/** `GET /api/vehicle-info/{id}` card: everything is optional; `equipment` can be text or a list. */
@Serializable
data class ODVehicleInfo(
    val carrier: String? = null,
    val fleetNo: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val year: String? = null,
    val status: String? = null,
    val equipment: JsonElement? = null,
    val note: String? = null,
    val depot: String? = null,
    val plate: String? = null,
    val url: String? = null,
) {
    val equipmentText: String?
        get() = when (val e = equipment) {
            // Backend sends one string like "klimatizace , USB nabíječky , Wi-Fi".
            is JsonPrimitive -> e.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ")
            is kotlinx.serialization.json.JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
            else -> null
        }?.takeIf { it.isNotBlank() }
}

@Serializable
data class ODVehicleInfoResponse(
    val available: Boolean = false,
    val pending: Boolean = false,
    val info: ODVehicleInfo? = null,
)

@Serializable
data class ODVehiclesResponse(val data: List<ODVehicle> = emptyList())

@Serializable
data class ODStop(
    val id: String,
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val provider: String? = null,
)

@Serializable
data class ODStopsResponse(val data: List<ODStop> = emptyList())

@Serializable
data class ODDeparture(
    val id: String? = null,
    val type: String = "bus",
    val line: String = "",
    val destination: String = "",
    val platform: String? = null,
    val plannedTime: String? = null,
    val expectedTime: String? = null,
    val delayMinutes: Int? = null,
    val cancelled: Boolean = false,
    val lowFloor: Boolean? = null,
) {
    /** Best estimate of the departure, epoch millis. */
    val whenMillis: Long? get() = parseIso(expectedTime) ?: parseIso(plannedTime)
}

@Serializable
data class ODDeparturesResponse(val stopName: String? = null, val data: List<ODDeparture> = emptyList())

@Serializable
data class ODRoute(
    val available: Boolean = false,
    val traveled: List<List<Double>> = emptyList(),
    val remaining: List<List<Double>> = emptyList(),
)

/** Tolerant ISO-8601 parser (`...Z`, `+01:00`, fractional seconds); null when it can't be read. */
fun parseIso(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim().replace(Regex("\\.\\d+"), "")
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (p in patterns) {
        val fmt = SimpleDateFormat(p, Locale.ROOT)
        if (p.endsWith("'Z'")) fmt.timeZone = TimeZone.getTimeZone("UTC")
        val parsed = runCatching { fmt.parse(s)?.time }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}
