package cz.euroklicmapa.odjezdy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Favourite stops, kept as JSON in `files/od_favorite_stops.json` (max 50, newest first). */
class ODFavoritesStore(context: Context) {
    private val file = File(context.filesDir, "od_favorite_stops.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ODStop.serializer())

    private val _stops = MutableStateFlow(load())
    val stops: StateFlow<List<ODStop>> = _stops.asStateFlow()

    fun isFavorite(id: String): Boolean = _stops.value.any { it.id == id }

    fun toggle(stop: ODStop) {
        val current = _stops.value
        _stops.value = if (current.any { it.id == stop.id }) current.filterNot { it.id == stop.id }
        else (listOf(stop) + current).take(MAX)
        save()
    }

    fun rename(fixed: ODStop) {
        _stops.value = _stops.value.map { if (it.id == fixed.id) it.copy(name = fixed.name) else it }
        save()
    }

    private fun load(): List<ODStop> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun save() {
        runCatching { file.writeText(json.encodeToString(serializer, _stops.value)) }
    }

    private companion object {
        const val MAX = 50
    }
}

/** A new name only for the same id at the same place (within 200 m), otherwise null: nothing is guessed. */
fun repairedFavorite(stored: ODStop, candidates: List<ODStop>): ODStop? {
    val match = candidates.firstOrNull { it.id == stored.id } ?: return null
    if (match.name.isBlank() || match.name == stored.name) return null
    val dy = (match.lat - stored.lat) * 111_320.0
    val dx = (match.lng - stored.lng) * 111_320.0 * kotlin.math.cos(Math.toRadians(stored.lat))
    return if (kotlin.math.hypot(dx, dy) <= 200.0) stored.copy(name = match.name) else null
}
