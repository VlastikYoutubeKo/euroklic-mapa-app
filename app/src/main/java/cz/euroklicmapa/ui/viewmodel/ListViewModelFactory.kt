package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.repository.EuroklicRepository

class ListViewModelFactory(
    private val repository: EuroklicRepository,
    private val locationRepository: LocationRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ListViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return ListViewModel(repository, locationRepository) as T
    }
}
