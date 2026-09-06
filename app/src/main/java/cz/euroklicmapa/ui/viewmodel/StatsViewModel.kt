package cz.euroklicmapa.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.repository.EuroklicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Snapshot for the "O projektu" stats card. Computed entirely from the Room cache
 * (offline-first, no new API call); badge semantics mirror the website:
 * "ověřeno" = at least one 👍, "nahlášeno" = 👎 outnumbers 👍.
 */
data class AppStats(
    val totalWc: Int,
    val totalPickup: Int,
    val verifiedWc: Int,
    val reportedWc: Int,
    val topLiked: PlaceListItem?,
    val topDisliked: PlaceListItem?,
)

class StatsViewModel(repository: EuroklicRepository) : ViewModel() {

    val stats: StateFlow<AppStats?> = combine(
        repository.getLocations(),
        repository.getPickupPoints(),
    ) { locations, pickupPoints ->
        val items = flattenPlaces(locations, pickupPoints, userLocation = null, category = PlaceCategory.ALL)
        val wc = items.filter { !it.isPickup }
        AppStats(
            totalWc = wc.size,
            totalPickup = items.size - wc.size,
            verifiedWc = wc.count { (it.likes ?: 0) > 0 },
            reportedWc = wc.count { val d = it.dislikes ?: 0; val l = it.likes ?: 0; d > 0 && d > l },
            topLiked = wc.filter { (it.likes ?: 0) > 0 }.maxByOrNull { it.likes ?: 0 },
            topDisliked = wc.filter { (it.dislikes ?: 0) > 0 }.maxByOrNull { it.dislikes ?: 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
