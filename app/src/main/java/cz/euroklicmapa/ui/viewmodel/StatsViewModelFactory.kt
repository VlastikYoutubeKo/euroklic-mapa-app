package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.euroklicmapa.data.repository.EuroklicRepository

class StatsViewModelFactory(
    private val repository: EuroklicRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StatsViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return StatsViewModel(repository) as T
    }
}
