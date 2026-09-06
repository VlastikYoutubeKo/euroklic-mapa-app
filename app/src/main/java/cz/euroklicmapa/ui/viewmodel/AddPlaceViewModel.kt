package cz.euroklicmapa.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.repository.AddPlaceRepository
import cz.euroklicmapa.data.repository.AddPlaceResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class AddPlaceViewModel(
    private val addPlaceRepository: AddPlaceRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    /** Roughly the geographic centre of Czechia — the fallback when we have no location fix. */
    val initialCenter: GeoPoint = locationRepository.lastKnown.value ?: GeoPoint(49.75, 15.5)

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var photoUri by mutableStateOf<Uri?>(null)
    var location by mutableStateOf(initialCenter)
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val _submitted = Channel<Unit>(Channel.BUFFERED)
    val submitted = _submitted.receiveAsFlow()

    val canSubmit: Boolean get() = name.isNotBlank() && !submitting

    fun onCenterChange(point: GeoPoint) {
        location = point
    }

    fun submit() {
        if (!canSubmit) return
        submitting = true
        error = null
        viewModelScope.launch {
            val result = addPlaceRepository.submit(
                name = name,
                description = description,
                lat = location.latitude,
                lon = location.longitude,
                photo = photoUri,
            )
            submitting = false
            when (result) {
                is AddPlaceResult.Success -> _submitted.trySend(Unit)
                is AddPlaceResult.Error -> error = result.message
            }
        }
    }
}

class AddPlaceViewModelFactory(
    private val addPlaceRepository: AddPlaceRepository,
    private val locationRepository: LocationRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AddPlaceViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return AddPlaceViewModel(addPlaceRepository, locationRepository) as T
    }
}
