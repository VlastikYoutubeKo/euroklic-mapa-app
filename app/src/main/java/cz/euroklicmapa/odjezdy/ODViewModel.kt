package cz.euroklicmapa.odjezdy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.util.t
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox

/** Vehicle kinds shown on the map, in the order of the filter chips. */
enum class ODVehicleType(val wire: String) {
    BUS("bus"), TRAM("tram"), TROLLEYBUS("trolleybus"), TRAIN("train"), SUBWAY("subway"), FERRY("ferry");

    val label: String
        get() = when (this) {
            BUS -> t("Autobus")
            TRAM -> t("Tramvaj")
            TROLLEYBUS -> t("Trolejbus")
            TRAIN -> t("Vlak")
            SUBWAY -> t("Metro")
            FERRY -> t("Přívoz")
        }

    companion object {
        fun of(wire: String?): ODVehicleType = entries.firstOrNull { it.wire == wire } ?: BUS
    }
}

data class ODMapState(
    val loading: Boolean = false,
    /** Vehicles inside the viewport that pass the type filter (capped, see [MAX_MARKERS]). */
    val visible: List<ODVehicle> = emptyList(),
    val totalInView: Int = 0,
    val zoomedOutTooFar: Boolean = false,
    val error: String? = null,
    val stops: List<ODStop> = emptyList(),
    val selectedVehicle: ODVehicle? = null,
    val route: ODRoute? = null,
    val info: ODVehicleInfo? = null,
    val infoPending: Boolean = false,
    val selectedStop: ODStop? = null,
    val departures: List<ODDeparture> = emptyList(),
    val departuresLoading: Boolean = false,
    val departuresError: String? = null,
    val filter: Set<ODVehicleType> = ODVehicleType.entries.toSet(),
)

class ODViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ODApi()
    private val favorites = ODFavoritesStore(app)

    private val _state = MutableStateFlow(ODMapState())
    val state: StateFlow<ODMapState> = _state.asStateFlow()
    val favoriteStops: StateFlow<List<ODStop>> = favorites.stops

    private var all: List<ODVehicle> = emptyList()
    private var viewport: BoundingBox? = null
    private var zoom: Double = 12.0
    private var active = false
    private var pollJob: Job? = null
    private var stopsJob: Job? = null
    private var departuresJob: Job? = null
    private var infoJob: Job? = null
    private val infoCache = HashMap<String, ODVehicleInfo>()

    // ---- Map lifecycle -----------------------------------------------------------------------

    /** Polls only while the map is visible and the app is in the foreground (the feed is nationwide, ~0.7 MB gzipped). */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        pollJob?.cancel()
        if (value) pollJob = viewModelScope.launch { pollLoop() }
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive && active) {
            refreshVehicles()
            delay(POLL_MS)
        }
    }

    private suspend fun refreshVehicles() {
        _state.update { it.copy(loading = all.isEmpty()) }
        try {
            all = api.vehicles()
            _state.update { it.copy(error = null, loading = false) }
            applyViewport()
        } catch (e: ODError) {
            _state.update { it.copy(loading = false, error = describe(e)) }
        }
    }

    fun onViewportChanged(box: BoundingBox, zoomLevel: Double) {
        viewport = box
        zoom = zoomLevel
        applyViewport()
        if (zoomLevel >= STOPS_MIN_ZOOM) loadStops(box) else _state.update { it.copy(stops = emptyList()) }
    }

    private fun applyViewport() {
        val box = viewport ?: return
        val filter = _state.value.filter
        val tooFar = box.latitudeSpan > MAX_SPAN_DEG
        val inView = if (tooFar) emptyList() else all.filter {
            it.inService && ODVehicleType.of(it.type) in filter &&
                it.location.lat in box.latSouth..box.latNorth && it.location.lng in box.lonWest..box.lonEast
        }
        _state.update {
            it.copy(
                visible = inView.take(MAX_MARKERS),
                totalInView = inView.size,
                zoomedOutTooFar = tooFar,
                selectedVehicle = it.selectedVehicle?.let { sel -> all.firstOrNull { v -> v.id == sel.id } ?: sel },
            )
        }
    }

    private fun loadStops(box: BoundingBox) {
        stopsJob?.cancel()
        stopsJob = viewModelScope.launch {
            delay(350) // debounce while panning
            runCatching { api.stopsIn(box.latSouth, box.latNorth, box.lonWest, box.lonEast) }
                .onSuccess { stops -> _state.update { it.copy(stops = stops.take(400)) } }
        }
    }

    fun toggleType(type: ODVehicleType) {
        _state.update {
            val next = if (type in it.filter) it.filter - type else it.filter + type
            it.copy(filter = next.ifEmpty { ODVehicleType.entries.toSet() })
        }
        applyViewport()
    }

    // ---- Selection ---------------------------------------------------------------------------

    fun selectVehicle(vehicle: ODVehicle?) {
        infoJob?.cancel()
        _state.update {
            it.copy(
                selectedVehicle = vehicle, route = null, selectedStop = null,
                info = vehicle?.let { v -> infoCache[v.id] }, infoPending = false,
            )
        }
        if (vehicle == null) return
        viewModelScope.launch {
            runCatching { api.route(vehicle.id) }
                .onSuccess { route -> _state.update { s -> if (s.selectedVehicle?.id == vehicle.id) s.copy(route = route) else s } }
        }
        // The vehicle card ("vizitka"): the server looks it up lazily, `pending` means try again in ~15 s.
        if (infoCache.containsKey(vehicle.id)) return
        infoJob = viewModelScope.launch {
            repeat(3) { attempt ->
                val resp = runCatching { api.vehicleInfo(vehicle.id) }.getOrNull() ?: return@launch
                _state.update { s ->
                    if (s.selectedVehicle?.id != vehicle.id) s
                    else s.copy(info = resp.info ?: s.info, infoPending = resp.pending && resp.info == null)
                }
                // Vehicle facts don't change: keep a finished card for the session (not a `pending` one).
                if (!resp.pending) resp.info?.let { infoCache[vehicle.id] = it }
                if (!resp.pending || attempt == 2) return@launch
                delay(15_000)
            }
        }
    }

    fun selectStop(stop: ODStop?) {
        departuresJob?.cancel()
        _state.update {
            it.copy(
                selectedStop = stop, selectedVehicle = null, route = null,
                departures = emptyList(), departuresError = null, departuresLoading = stop != null,
            )
        }
        if (stop == null) return
        departuresJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val resp = api.departures(stop.id)
                    _state.update { it.copy(departures = resp.data, departuresLoading = false, departuresError = null) }
                } catch (e: ODError) {
                    _state.update { it.copy(departuresLoading = false, departuresError = describe(e)) }
                }
                delay(DEPARTURES_MS)
            }
        }
    }

    // ---- Search & favorites ------------------------------------------------------------------

    suspend fun searchStops(query: String): Result<List<ODStop>> =
        try {
            Result.success(api.searchStops(query))
        } catch (e: ODError) {
            Result.failure(e)
        }

    fun toggleFavorite(stop: ODStop) = favorites.toggle(stop)

    fun describe(e: ODError): String = when (e) {
        ODError.Offline -> t("Bez připojení k internetu.")
        is ODError.RateLimited -> t("Server dočasně omezil počet dotazů. Zkus to za chvíli.")
        is ODError.Server -> t("Server odpověděl chybou (%s).", e.code)
        ODError.BadData -> t("Odpověď serveru se nepodařilo zpracovat.")
    }

    companion object {
        const val POLL_MS = 16_000L
        const val DEPARTURES_MS = 20_000L
        const val MAX_MARKERS = 350
        /** Above this latitude span (≈ country-wide) vehicles are not drawn. */
        const val MAX_SPAN_DEG = 1.5
        const val STOPS_MIN_ZOOM = 14.0
    }
}
