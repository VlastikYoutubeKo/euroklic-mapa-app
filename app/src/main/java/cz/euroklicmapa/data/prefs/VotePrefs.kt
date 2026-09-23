package cz.euroklicmapa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Local "did I already vote on this place" memory. `/api_vote.php` is anonymous (no login) and
 * dedups server-side by IP — the app itself never persisted which way it voted anywhere, so
 * [cz.euroklicmapa.ui.viewmodel.DetailViewModel]'s `myVote` reset to unset on every fresh process
 * (app update, force-stop, reboot — not just updates, though that's when a user notices), and the
 * vote buttons looked untouched even for a place the user already voted on. Shares the single
 * `"settings"` DataStore ([settingsDataStore]) — never declare a second
 * `preferencesDataStore("settings")`, it crashes.
 */
class VotePrefs(private val appContext: Context) {

    private val store get() = appContext.settingsDataStore

    /** `true` = voted "funguje", `false` = voted "nefunguje", absent = never voted (that we know of). */
    suspend fun getVote(locationId: Int): Boolean? =
        parse(store.data.first()[VOTES] ?: "")[locationId]

    suspend fun setVote(locationId: Int, like: Boolean) {
        store.edit { prefs ->
            val votes = parse(prefs[VOTES] ?: "").toMutableMap()
            votes[locationId] = like
            prefs[VOTES] = votes.entries.joinToString(",") { (id, l) -> "$id:${if (l) 1 else 0}" }
        }
    }

    private fun parse(raw: String): Map<Int, Boolean> =
        raw.split(',')
            .mapNotNull { entry ->
                val (idPart, typePart) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val id = idPart.trim().toIntOrNull() ?: return@mapNotNull null
                id to (typePart.trim() == "1")
            }
            .toMap()

    private companion object {
        val VOTES = stringPreferencesKey("place_votes")
    }
}
