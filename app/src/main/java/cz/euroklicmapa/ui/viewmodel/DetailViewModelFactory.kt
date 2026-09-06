package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.repository.AddPlaceRepository
import cz.euroklicmapa.data.repository.EuroklicRepository
import cz.euroklicmapa.data.repository.FavoritesRepository

class DetailViewModelFactory(
    private val repository: EuroklicRepository,
    private val locationRepository: LocationRepository,
    private val favoritesRepository: FavoritesRepository,
    private val addPlaceRepository: AddPlaceRepository,
    private val id: Int,
    private val type: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DetailViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return DetailViewModel(
            repository, locationRepository, favoritesRepository, addPlaceRepository, id, type,
        ) as T
    }
}
