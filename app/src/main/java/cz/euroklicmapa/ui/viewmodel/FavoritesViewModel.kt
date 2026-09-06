package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.repository.FavoritesRepository
import cz.euroklicmapa.util.placeStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.osmdroid.util.GeoPoint

class FavoritesViewModel(
    private val favorites: FavoritesRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    init {
        locationRepository.refresh()
    }

    val items: StateFlow<List<PlaceListItem>> = combine(
        favorites.observeAll(),
        locationRepository.lastKnown,
    ) { favs, userLocation ->
        favs.map { f ->
            val pos = GeoPoint(f.latitude, f.longitude)
            PlaceListItem(
                id = f.placeId,
                isPickup = f.isPickup,
                title = f.title,
                subtitle = null,
                position = pos,
                source = f.source,
                status = if (f.isPickup) null
                else placeStatus(f.source, f.likes, f.dislikes, f.lastVerified),
                country = null,
                likes = f.likes,
                dislikes = f.dislikes,
                distanceMeters = userLocation?.let { pos.distanceToAsDouble(it) },
            )
        }.let { list ->
            if (userLocation != null) list.sortedBy { it.distanceMeters ?: Double.MAX_VALUE } else list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class FavoritesViewModelFactory(
    private val favorites: FavoritesRepository,
    private val locationRepository: LocationRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FavoritesViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return FavoritesViewModel(favorites, locationRepository) as T
    }
}
