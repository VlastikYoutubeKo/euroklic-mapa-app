package cz.euroklicmapa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * The last location the app saw. Widgets can't ask for a fix themselves (background location needs
 * a separate permission), so they use what the app stored while it was open. Shares the single
 * `"settings"` DataStore ([settingsDataStore]) — never declare a second `preferencesDataStore("settings")`.
 */
class LastLocationPrefs(private val appContext: Context) {
    private val store get() = appContext.settingsDataStore

    data class Saved(val lat: Double, val lon: Double, val atMillis: Long)

    suspend fun save(lat: Double, lon: Double) {
        store.edit {
            it[LAT] = lat
            it[LON] = lon
            it[AT] = System.currentTimeMillis()
        }
    }

    suspend fun read(): Saved? {
        val p = store.data.first()
        val lat = p[LAT] ?: return null
        val lon = p[LON] ?: return null
        return Saved(lat, lon, p[AT] ?: 0L)
    }

    private companion object {
        val LAT = doublePreferencesKey("last_loc_lat")
        val LON = doublePreferencesKey("last_loc_lon")
        val AT = longPreferencesKey("last_loc_at")
    }
}
