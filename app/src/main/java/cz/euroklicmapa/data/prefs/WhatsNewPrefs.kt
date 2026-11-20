package cz.euroklicmapa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Remembers which app version already showed its "What's new" dialog. Shares the single
 * `"settings"` DataStore ([settingsDataStore]) — never declare a second `preferencesDataStore("settings")`.
 */
class WhatsNewPrefs(private val appContext: Context) {
    private val store get() = appContext.settingsDataStore

    /** Empty on a fresh install (so the first launch shows nothing). */
    suspend fun lastSeenVersion(): String = store.data.first()[KEY].orEmpty()

    suspend fun setLastSeenVersion(version: String) {
        store.edit { it[KEY] = version }
    }

    private companion object {
        val KEY = stringPreferencesKey("last_seen_version")
    }
}
