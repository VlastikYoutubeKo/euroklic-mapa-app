package cz.euroklicmapa.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.osmdroid.util.GeoPoint
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
private data class NominatimHit(val lat: String, val lon: String, val display_name: String = "")

private interface NominatimApi {
    @GET("search?format=json&limit=1&addressdetails=0&countrycodes=cz,sk")
    suspend fun search(@Query("q") query: String): List<NominatimHit>
}

data class GeocodeResult(val point: GeoPoint, val label: String)

/**
 * Address search — same approach as the website: a direct call to the public Nominatim API,
 * no own key/proxy. Nominatim's usage policy requires an identifying User-Agent; results are
 * restricted to CZ/SK via `countrycodes`.
 */
class GeocodingRepository {

    private val api: NominatimApi = run {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "EuroklicMapa/1.0 (Android; euroklic.odjezdy.online)")
                        .build(),
                )
            }
            .build()
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NominatimApi::class.java)
    }

    suspend fun search(query: String): GeocodeResult? = try {
        api.search(query)
            .firstOrNull()
            ?.let { GeocodeResult(GeoPoint(it.lat.toDouble(), it.lon.toDouble()), it.display_name) }
    } catch (e: Exception) {
        android.util.Log.w("Geocoding", "search failed for \"$query\"", e)
        null
    }
}
