package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.remote.GeocodingRepository
import cz.euroklicmapa.data.repository.EuroklicRepository

class MapViewModelFactory(
    private val repository: EuroklicRepository,
    private val locationRepository: LocationRepository,
    private val geocoding: GeocodingRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MapViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return MapViewModel(repository, locationRepository, geocoding) as T
    }
}
