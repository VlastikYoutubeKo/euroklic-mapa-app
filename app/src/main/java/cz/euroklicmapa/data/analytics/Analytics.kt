package cz.euroklicmapa.data.analytics

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import cz.euroklicmapa.data.prefs.settingsDataStore
import cz.euroklicmapa.util.appVersionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** The only events the server accepts (whitelist on `api_app_event.php`). */
enum class AnalyticsEvent(val wire: String) {
    APP_OPEN("app_open"),
    PLACE_OPEN("place_open"),
    NAVIGATE("navigate"),
    SEARCH("search"),
    VOTE("vote"),
    PLACE_ADD("place_add"),
    COMMENT_ADD("comment_add"),
    REPORT("report"),
    MODE_DEPARTURES("mode_departures"),
}

/**
 * Voluntary, anonymous usage counters. **Off by default**: nothing is sent unless the user turns on
 * "Pomoci vylepšit aplikaci anonymním měřením" in More. The request carries only
 * `{"event","platform","version"}` — no location, search text, account or device id — and uses its own
 * plain OkHttp client (no auth interceptor, no cookie jar). Errors are ignored (fire and forget).
 * Described in PRIVACY.md.
 */
object Analytics {
    private const val URL = "https://euroklic.odjezdy.online/api_app_event.php"
    private val ENABLED = booleanPreferencesKey("analytics_enabled")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun enabled(context: Context): Flow<Boolean> =
        context.applicationContext.settingsDataStore.data.map { it[ENABLED] ?: false }

    suspend fun setEnabled(context: Context, value: Boolean) {
        context.applicationContext.settingsDataStore.edit { it[ENABLED] = value }
    }

    fun track(event: AnalyticsEvent) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                if (!enabled(context).first()) return@launch
                // Full name incl. a beta suffix ("1.4.0-beta.1"); the server accepts x.y.z[-beta.N].
                val version = appVersionName(context)
                val json = """{"event":"${event.wire}","platform":"android","version":"$version"}"""
                val request = Request.Builder()
                    .url(URL)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().close()
            }
        }
    }
}
