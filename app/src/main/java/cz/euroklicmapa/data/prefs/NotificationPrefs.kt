package cz.euroklicmapa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Notification opt-ins + the dedup bookkeeping the phase-1 [cz.euroklicmapa.notifications.QueuePollWorker]
 * needs. Shares the single `"settings"` DataStore ([settingsDataStore]) — never declare a second
 * `preferencesDataStore("settings")`, it crashes.
 */
class NotificationPrefs(private val appContext: Context) {

    private val store get() = appContext.settingsDataStore

    /** Admin-queue "něco čeká" alert. Default on — only fires for signed-in admins anyway. */
    val adminQueueEnabled: Flow<Boolean> =
        store.data.map { it[ADMIN_QUEUE] ?: true }

    /** "Nové místo v okolí" alert. Opt-in — default off. */
    val nearbyEnabled: Flow<Boolean> =
        store.data.map { it[NEARBY] ?: false }

    suspend fun setAdminQueueEnabled(value: Boolean) {
        store.edit { it[ADMIN_QUEUE] = value }
    }

    suspend fun setNearbyEnabled(value: Boolean) {
        store.edit { it[NEARBY] = value }
    }

    // ---- dedup state (one-shot reads/writes from the worker) ----

    suspend fun lastAdminPlaceCount(): Int = store.data.first()[LAST_ADMIN_PLACE_COUNT] ?: -1

    suspend fun lastAdminPhotoCount(): Int = store.data.first()[LAST_ADMIN_PHOTO_COUNT] ?: -1

    suspend fun setLastAdminCounts(placeCount: Int, photoCount: Int) {
        store.edit {
            it[LAST_ADMIN_PLACE_COUNT] = placeCount
            it[LAST_ADMIN_PHOTO_COUNT] = photoCount
        }
    }

    /** Empty set means "not seeded yet" — the worker seeds silently on first run, no notification. */
    suspend fun knownPlaceIds(): Set<Int> =
        (store.data.first()[KNOWN_PLACE_IDS] ?: "")
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

    suspend fun setKnownPlaceIds(ids: Set<Int>) {
        store.edit { it[KNOWN_PLACE_IDS] = ids.joinToString(",") }
    }

    suspend fun lastNearbyNotifEpoch(): Long = store.data.first()[LAST_NEARBY_NOTIF_EPOCH] ?: 0L

    suspend fun setLastNearbyNotifEpoch(epochMs: Long) {
        store.edit { it[LAST_NEARBY_NOTIF_EPOCH] = epochMs }
    }

    private companion object {
        val ADMIN_QUEUE = booleanPreferencesKey("notif_admin_queue")
        val NEARBY = booleanPreferencesKey("notif_nearby")
        val LAST_ADMIN_PLACE_COUNT = intPreferencesKey("last_admin_place_count")
        val LAST_ADMIN_PHOTO_COUNT = intPreferencesKey("last_admin_photo_count")
        val KNOWN_PLACE_IDS = stringPreferencesKey("known_place_ids")
        val LAST_NEARBY_NOTIF_EPOCH = longPreferencesKey("last_nearby_notif_epoch")
    }
}
