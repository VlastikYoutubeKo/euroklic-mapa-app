package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.repository.EuroklicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListViewModel(
    private val repository: EuroklicRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    init {
        locationRepository.refresh()
    }

    private val _category = MutableStateFlow(PlaceCategory.ALL)
    val category: StateFlow<PlaceCategory> = _category.asStateFlow()

    fun setCategory(c: PlaceCategory) { _category.value = c }

    private val _filters = MutableStateFlow(PlaceFilters())
    val filters: StateFlow<PlaceFilters> = _filters.asStateFlow()

    fun setFilters(f: PlaceFilters) { _filters.value = f }

    val dataSync: StateFlow<Long?> = repository.observeLocationsSync()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasLocation: StateFlow<Boolean> = locationRepository.lastKnown
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val items: StateFlow<List<PlaceListItem>> = combine(
        repository.getLocations(),
        repository.getPickupPoints(),
        locationRepository.lastKnown,
        _category,
        _filters,
    ) { locations, pickupPoints, userLocation, category, filters ->
        applyPlaceFilters(flattenPlaces(locations, pickupPoints, userLocation, category), filters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshLocation() = locationRepository.refresh()

    /** Manual re-fetch for the "Zkusit znovu" button — e.g. after a failed first launch offline. */
    fun retry() {
        viewModelScope.launch {
            repository.refreshLocations()
            repository.refreshPickupPoints()
        }
    }
}
