package cz.euroklicmapa.odjezdy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Failure of an Odjezdy request; [message] is shown to the user via `t()` at the call site. */
sealed class ODError : Exception() {
    object Offline : ODError()
    data class RateLimited(val untilMillis: Long) : ODError()
    data class Server(val code: Int) : ODError()
    object BadData : ODError()
}

/**
 * Read-only client for `https://app.odjezdy.online` (no key; 150 requests/min per IP). Respects 429 by
 * pausing until `X-RateLimit-Reset`, keeps a short per-URL cache and never runs the same request twice at once.
 */
class ODApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Volatile private var blockedUntil = 0L
    private val cache = HashMap<String, Pair<Long, String>>()

    private suspend fun get(path: String, query: Map<String, String> = emptyMap(), ttlMs: Long = 0): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (now < blockedUntil) throw ODError.RateLimited(blockedUntil)
            val url = BASE.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
                query.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val key = url.toString()
            synchronized(cache) {
                cache[key]?.let { (at, body) -> if (now - at < ttlMs) return@withContext body }
            }
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 429) {
                        val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000)
                        blockedUntil = minOf(reset ?: (now + 30_000), now + 120_000)
                        throw ODError.RateLimited(blockedUntil)
                    }
                    if (!resp.isSuccessful) throw ODError.Server(resp.code)
                    val body = resp.body?.string() ?: throw ODError.BadData
                    if (ttlMs > 0) synchronized(cache) { cache[key] = now to body }
                    body
                }
            } catch (e: IOException) {
                throw ODError.Offline
            }
        }

    private suspend inline fun <reified T> decode(body: String): T = withContext(Dispatchers.Default) {
        runCatching { json.decodeFromString<T>(body) }.getOrElse { throw ODError.BadData }
    }

    suspend fun vehicles(): List<ODVehicle> =
        decode<ODVehiclesResponse>(get("api/vehicles", mapOf("fields" to "map"), ttlMs = 8_000)).data

    suspend fun stopsIn(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<ODStop> {
        val body = get(
            "api/stations/bounds",
            mapOf("minLat" to "$minLat", "maxLat" to "$maxLat", "minLng" to "$minLng", "maxLng" to "$maxLng"),
            ttlMs = 30_000,
        )
        return withContext(Dispatchers.Default) {
            runCatching { json.decodeFromString(ListSerializer(ODStop.serializer()), body) }
                .getOrElse { throw ODError.BadData }
        }
    }

    suspend fun searchStops(query: String): List<ODStop> =
        decode<ODStopsResponse>(get("api/v2/stops/search", mapOf("q" to query), ttlMs = 60_000)).data

    suspend fun departures(stopId: String, limit: Int = 60, minutes: Int = 240): ODDeparturesResponse =
        decode(get("api/v2/departures", mapOf("stopId" to stopId, "limit" to "$limit", "minutes" to "$minutes"), ttlMs = 15_000))

    suspend fun vehicleInfo(vehicleId: String): ODVehicleInfoResponse =
        decode(get("api/vehicle-info/${java.net.URLEncoder.encode(vehicleId, "UTF-8")}", ttlMs = 5_000))

    suspend fun route(vehicleId: String): ODRoute =
        decode(get("api/vehicle-route/${java.net.URLEncoder.encode(vehicleId, "UTF-8")}", ttlMs = 15_000))

    private companion object {
        const val BASE = "https://app.odjezdy.online/"
    }
}
