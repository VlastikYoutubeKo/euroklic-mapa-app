package cz.euroklicmapa.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
private data class OdjezdyStation(
    val id: String = "",
    val name: String = "",
    val lat: Double = Double.NaN,
    val lng: Double = Double.NaN,
)

private interface OdjezdyApi {
    @GET("api/stations/bounds")
    suspend fun bounds(
        @Query("minLat") minLat: Double,
        @Query("maxLat") maxLat: Double,
        @Query("minLng") minLng: Double,
        @Query("maxLng") maxLng: Double,
    ): List<OdjezdyStation>
}

/** A public-transport stop near a toilet; [distanceM] is straight-line metres. */
data class NearbyStop(val id: String, val name: String, val distanceM: Int)

/** Pure: sort by distance, dedupe by name (platform posts of one stop), keep [max]. */
internal fun nearestStops(stops: List<NearbyStop>, max: Int = 3): List<NearbyStop> = stops
    .filter { it.name.isNotBlank() }
    .sortedBy { it.distanceM }
    .distinctBy { it.name }
    .take(max)

/**
 * Nearby stops from the public odjezdy.online JSON (no key; its terms allow reasonable personal
 * use of the public endpoints). Own client WITHOUT the auth interceptor, so the Bearer token
 * never goes to a third party. Any failure -> empty list (the UI hides the card).
 */
class NearbyStopsRepository {
    private val api: OdjezdyApi = run {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "EuroklicMapa/1.0 (Android; euroklic.odjezdy.online)")
                        .build(),
                )
            }
            .build()
        Retrofit.Builder()
            .baseUrl("https://app.odjezdy.online/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OdjezdyApi::class.java)
    }

    suspend fun nearby(lat: Double, lon: Double, max: Int = 3): List<NearbyStop> = try {
        val all = api.bounds(lat - 0.008, lat + 0.008, lon - 0.012, lon + 0.012)
            .filter { it.lat.isFinite() && it.lng.isFinite() }
            .map {
                val res = FloatArray(1)
                android.location.Location.distanceBetween(lat, lon, it.lat, it.lng, res)
                NearbyStop(it.id, it.name, res[0].toInt())
            }
        nearestStops(all, max)
    } catch (e: Exception) {
        android.util.Log.w("NearbyStops", "fetch failed", e)
        emptyList()
    }
}
