package cz.euroklicmapa

import android.app.Application
import androidx.room.Room
import cz.euroklicmapa.data.auth.AuthRepository
import cz.euroklicmapa.data.auth.AuthTokenHolder
import cz.euroklicmapa.data.auth.SecureTokenStore
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.local.EuroklicDatabase
import cz.euroklicmapa.data.prefs.ThemeRepository
import cz.euroklicmapa.data.remote.AuthInterceptor
import cz.euroklicmapa.data.remote.EuroklicApi
import cz.euroklicmapa.data.remote.GeocodingRepository
import cz.euroklicmapa.data.repository.AddPlaceRepository
import cz.euroklicmapa.data.repository.AdminRepository
import cz.euroklicmapa.data.repository.EuroklicRepository
import cz.euroklicmapa.data.repository.EuroklicRepositoryImpl
import cz.euroklicmapa.data.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manual DI container. The app is small enough that a ViewModelProvider.Factory per screen
 * pulling from here is simpler than a DI framework.
 */
class EuroklicApplication : Application() {

    lateinit var repository: EuroklicRepository
        private set

    lateinit var locationRepository: LocationRepository
        private set

    lateinit var favoritesRepository: FavoritesRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var addPlaceRepository: AddPlaceRepository
        private set

    lateinit var adminRepository: AdminRepository
        private set

    val geocodingRepository: GeocodingRepository by lazy { GeocodingRepository() }
    val themeRepository: ThemeRepository by lazy { ThemeRepository(applicationContext) }

    /**
     * Set by the "Nejbližší WC" launcher shortcut (static intent action handled in
     * MainActivity); consumed once by MapScreen, which runs the nearest-place flow.
     */
    private val _pendingNearestShortcut = MutableStateFlow(false)
    val pendingNearestShortcut: StateFlow<Boolean> = _pendingNearestShortcut.asStateFlow()

    fun requestNearestShortcut() {
        _pendingNearestShortcut.value = true
    }

    fun consumeNearestShortcut() {
        _pendingNearestShortcut.value = false
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val database = Room.databaseBuilder(
            this,
            EuroklicDatabase::class.java,
            "euroklic.db",
        )
            .addMigrations(
                EuroklicDatabase.MIGRATION_3_4,
                EuroklicDatabase.MIGRATION_4_5,
                EuroklicDatabase.MIGRATION_5_6,
                EuroklicDatabase.MIGRATION_6_7,
            )
            // Fallback for the un-migrated v1/v2/v3 gaps; from v4 on `favorites` is preserved.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        val tokenHolder = AuthTokenHolder()
        val secureTokenStore = SecureTokenStore()

        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            // The feeds are single large GeoJSON payloads built on the fly by a PHP+SQLite
            // backend; the default 10 s read timeout is too tight on a slow link.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            // `/api_locations.php` + `/api_pickup_points.php` send `ETag` + `Cache-Control:
            // no-cache` (revalidate every time). OkHttp's cache turns each refresh into a
            // conditional GET (`If-None-Match: "<etag>"`); on `304` it replays the stored body
            // so the repository still gets a full FeatureCollection but nothing crossed the
            // wire. The cache key is the full URL incl. query string, so the `near=`/`bbox=`
            // variants each keep their own ETag, exactly as the backend computes them. Other
            // endpoints send no cache headers, so nothing else is stored.
            .cache(Cache(File(cacheDir, "http_cache"), 20L * 1024 * 1024))
            // Voting keeps an anonymous PHPSESSID across /api_csrf.php → /api_vote.php.
            .cookieJar(SessionCookieJar())
            // Adds `Authorization: Bearer` when signed in; clears the token on a 401; swaps in a
            // fresher token when the server returns one via `X-Refreshed-Token`.
            .addInterceptor(
                AuthInterceptor(
                    tokenHolder,
                    onUnauthorized = { if (::authRepository.isInitialized) authRepository.onUnauthorized() },
                    onTokenRefreshed = { if (::authRepository.isInitialized) authRepository.onTokenRefreshed(it) },
                ),
            )
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(EuroklicApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(EuroklicApi::class.java)

        locationRepository = LocationRepository(applicationContext)
        repository = EuroklicRepositoryImpl(api, database.dao(), locationRepository)
        favoritesRepository = FavoritesRepository(database.dao())
        authRepository = AuthRepository(applicationContext, api, tokenHolder, secureTokenStore, appScope)
        addPlaceRepository = AddPlaceRepository(api, applicationContext)
        adminRepository = AdminRepository(api)
    }
}

/** In-memory cookie jar, per host, merged by name. Enough to hold one session cookie. */
private class SessionCookieJar : CookieJar {
    private val byHost = HashMap<String, MutableMap<String, Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val jar = byHost.getOrPut(url.host) { HashMap() }
        for (c in cookies) jar[c.name] = c
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val jar = byHost[url.host] ?: return emptyList()
        jar.values.removeAll { it.expiresAt < now }
        return jar.values.toList()
    }
}
