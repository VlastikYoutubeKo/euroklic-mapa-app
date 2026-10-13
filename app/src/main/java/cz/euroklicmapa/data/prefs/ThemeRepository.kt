package cz.euroklicmapa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class DesignVariant { SOUCASNY, KONTRAST, SVEZI, ZNACENI }

/**
 * The one and only `"settings"` DataStore declaration. Declaring `preferencesDataStore("settings")`
 * a second time anywhere in the process crashes at first access — [NotificationPrefs] reuses this.
 */
internal val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val THEME_KEY = stringPreferencesKey("theme_mode")
private val DESIGN_KEY = stringPreferencesKey("design_variant")

class ThemeRepository(private val appContext: Context) {

    val variant: Flow<DesignVariant> = appContext.settingsDataStore.data.map { prefs ->
        runCatching { DesignVariant.valueOf(prefs[DESIGN_KEY] ?: DesignVariant.SOUCASNY.name) }
            .getOrDefault(DesignVariant.SOUCASNY)
    }

    suspend fun setVariant(variant: DesignVariant) {
        appContext.settingsDataStore.edit { it[DESIGN_KEY] = variant.name }
    }

    val mode: Flow<ThemeMode> = appContext.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun set(mode: ThemeMode) {
        appContext.settingsDataStore.edit { it[THEME_KEY] = mode.name }
    }
}
