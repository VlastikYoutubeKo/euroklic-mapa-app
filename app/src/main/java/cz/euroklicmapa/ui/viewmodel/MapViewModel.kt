package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.remote.GeocodingRepository
import cz.euroklicmapa.data.repository.EuroklicRepository
import cz.euroklicmapa.ui.map.MapMarker
import cz.euroklicmapa.util.placeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class MapViewModel(
    private val repository: EuroklicRepository,
    private val locationRepository: LocationRepository,
    private val geocoding: GeocodingRepository,
) : ViewModel() {

    /** Same three-way filter as the Seznam tab; drives both the markers and the sheet list. */
    private val _category = MutableStateFlow(PlaceCategory.ALL)
    val category: StateFlow<PlaceCategory> = _category.asStateFlow()

    fun setCategory(c: PlaceCategory) { _category.value = c }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    /** Set when the camera should move; the screen consumes it and calls [onRecenterHandled]. */
    private val _recenterTarget = MutableStateFlow<GeoPoint?>(null)
    val recenterTarget: StateFlow<GeoPoint?> = _recenterTarget.asStateFlow()

    val userLocation: StateFlow<GeoPoint?> = locationRepository.lastKnown

    /** One-shot result of the "Nejbližší WC" action; screen consumes it via [onNearestResultHandled]. */
    private val _nearestResult = MutableStateFlow<NearestResult?>(null)
    val nearestResult: StateFlow<NearestResult?> = _nearestResult.asStateFlow()

    val dataSync: StateFlow<Long?> = repository.observeLocationsSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val markers: StateFlow<List<MapMarker>> = combine(
        repository.getLocations(),
        repository.getPickupPoints(),
        _category,
    ) { locations, pickupPoints, category ->
        val wc = if (category == PlaceCategory.PICKUP) emptyList() else locations.map {
            MapMarker(
                id = "wc_${it.id}",
                position = GeoPoint(it.latitude, it.longitude),
                title = it.name,
                isPickup = false,
                source = it.source,
                status = placeStatus(it.source, it.likes, it.dislikes, it.lastVerified),
                likes = it.likes,
                dislikes = it.dislikes,
            )
        }
        val pp = if (category == PlaceCategory.TOILET) emptyList() else pickupPoints.map {
            MapMarker("pp_${it.id}", GeoPoint(it.latitude, it.longitude), it.orgName, true, precision = it.precision)
        }
        wc + pp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The persistent bottom sheet's list — nearest first when a location is known. */
    val nearbyPlaces: StateFlow<List<PlaceListItem>> = combine(
        repository.getLocations(),
        repository.getPickupPoints(),
        locationRepository.lastKnown,
        _category,
    ) { locations, pickupPoints, userLocation, category ->
        flattenPlaces(locations, pickupPoints, userLocation, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(q: String) {
        _searchQuery.value = q
        _searchError.value = null
    }

    fun submitSearch() {
        val q = _searchQuery.value.trim()
        if (q.isEmpty() || _isSearching.value) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            val hit = geocoding.search(q)
            _isSearching.value = false
            if (hit != null) _recenterTarget.value = hit.point
            else _searchError.value = "Adresu se nepodařilo najít"
        }
    }

    /** Called once the location permission is granted, or from the FAB. */
    fun locateUser() {
        locationRepository.refresh { point -> if (point != null) _recenterTarget.value = point }
    }

    /**
     * The "Nejbližší WC" quick action: refresh the location fix, then recentre on and select
     * the nearest marker within the active category filter. Emits exactly one [NearestResult].
     */
    fun findNearest() {
        locationRepository.refresh { point ->
            if (point == null) {
                _nearestResult.value = NearestResult.NoLocation
                return@refresh
            }
            val nearest = markers.value.minByOrNull { it.position.distanceToAsDouble(point) }
            if (nearest == null) {
                _nearestResult.value = NearestResult.NoPlaces
            } else {
                _recenterTarget.value = nearest.position
                _nearestResult.value = NearestResult.Found(nearest)
            }
        }
    }

    fun onNearestResultHandled() {
        _nearestResult.value = null
    }

    fun onRecenterHandled() {
        _recenterTarget.value = null
    }
}

/** Outcome of the "Nejbližší WC" quick action. */
sealed interface NearestResult {
    data class Found(val marker: MapMarker) : NearestResult
    data object NoLocation : NearestResult
    data object NoPlaces : NearestResult
}
