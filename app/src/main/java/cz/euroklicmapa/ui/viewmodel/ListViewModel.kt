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
    ) { locations, pickupPoints, userLocation, category ->
        flattenPlaces(locations, pickupPoints, userLocation, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshLocation() = locationRepository.refresh()
}
