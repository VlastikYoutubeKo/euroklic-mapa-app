package cz.euroklicmapa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which half of the app is on screen: the Euroklíč map (toilets) or Odjezdy (public transport). */
enum class AppMode { EUROKLIC, ODJEZDY }

/** Shares the single `"settings"` DataStore ([settingsDataStore]) — never declare a second `preferencesDataStore("settings")`. */
class AppModePrefs(private val appContext: Context) {
    val mode: Flow<AppMode> = appContext.settingsDataStore.data.map { prefs ->
        if (prefs[KEY] == AppMode.ODJEZDY.name) AppMode.ODJEZDY else AppMode.EUROKLIC
    }

    suspend fun set(mode: AppMode) {
        appContext.settingsDataStore.edit { it[KEY] = mode.name }
        if (mode == AppMode.ODJEZDY) cz.euroklicmapa.data.analytics.Analytics.track(cz.euroklicmapa.data.analytics.AnalyticsEvent.MODE_DEPARTURES)
    }

    private companion object {
        val KEY = stringPreferencesKey("app_mode")
    }
}
