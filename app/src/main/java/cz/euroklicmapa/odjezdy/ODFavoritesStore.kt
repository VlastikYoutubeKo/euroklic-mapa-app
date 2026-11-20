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
