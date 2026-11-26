package cz.euroklicmapa.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.euroklicmapa.data.prefs.settingsDataStore
import cz.euroklicmapa.util.appVersionName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A newer stable release than the installed one. */
data class UpdateInfo(val version: String, val page: String)

/**
 * "A new version is available" hint. The app is installed from a file, so nothing updates it
 * automatically. Asks `api_app_version.php` (latest stable GitHub Release) at most every 12 hours;
 * a plain anonymous GET (no account, no device id), see PRIVACY.md. Beta builds never nag.
 */
object UpdateChecker {
    private const val URL = "https://euroklic.odjezdy.online/api_app_version.php"
    private const val PAGE_PREFIX = "https://euroklic.odjezdy.online/"
    private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000

    private val LAST_CHECK = longPreferencesKey("update_last_check")
    private val LATEST_VERSION = stringPreferencesKey("update_latest_version")
    private val LATEST_PAGE = stringPreferencesKey("update_latest_page")
    private val DISMISSED = stringPreferencesKey("update_dismissed_version")
    private val ANNOUNCED = stringPreferencesKey("update_announced_version")

    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    @Serializable
    private data class Remote(val version: String = "", val page: String = "")

    /** `true` when [remote] (x.y.z) is a higher release than [local]; a beta/suffixed [local] never is. */
    fun isNewer(remote: String, local: String): Boolean {
        if (local.contains('-')) return false
        val r = numbers(remote) ?: return false
        val l = numbers(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbers(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }

    /** The update to offer now, or `null` (none, already dismissed, or running a beta). */
    fun available(context: Context): Flow<UpdateInfo?> {
        val current = appVersionName(context)
        return context.applicationContext.settingsDataStore.data.map { prefs ->
            val version = prefs[LATEST_VERSION] ?: return@map null
            val page = prefs[LATEST_PAGE] ?: return@map null
            if (version == prefs[DISMISSED] || !isNewer(version, current)) null else UpdateInfo(version, page)
        }
    }

    /** Asks the server unless the last check is newer than 12 hours. Errors are ignored. */
    suspend fun refreshIfDue(context: Context, now: Long = System.currentTimeMillis()) {
        val store = context.applicationContext.settingsDataStore
        val last = store.data.first()[LAST_CHECK] ?: 0L
        if (now - last < CHECK_EVERY_MS) return
        val remote = runCatching { fetch() }.getOrNull() ?: return
        store.edit {
            it[LAST_CHECK] = now
            it[LATEST_VERSION] = remote.version
            it[LATEST_PAGE] = remote.page
        }
    }

    private suspend fun fetch(): Remote? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(URL).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val remote = json.decodeFromString<Remote>(body)
            // Only trust our own page; a wrong link must never become a button.
            if (numbers(remote.version) == null || !remote.page.startsWith(PAGE_PREFIX)) null else remote
        }
    }

    suspend fun dismiss(context: Context, version: String) {
        context.applicationContext.settingsDataStore.edit { it[DISMISSED] = version }
    }

    /** Returns the update once per version (for a single heads-up toast), then `null`. */
    suspend fun takeAnnouncement(context: Context): UpdateInfo? {
        val info = available(context).first() ?: return null
        val store = context.applicationContext.settingsDataStore
        if (store.data.first()[ANNOUNCED] == info.version) return null
        store.edit { it[ANNOUNCED] = info.version }
        return info
    }
}
