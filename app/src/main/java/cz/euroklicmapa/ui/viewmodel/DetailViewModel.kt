package cz.euroklicmapa.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.model.WcComment
import cz.euroklicmapa.data.repository.AddPhotoResult
import cz.euroklicmapa.data.repository.AddPlaceRepository
import cz.euroklicmapa.data.repository.EuroklicRepository
import cz.euroklicmapa.data.repository.FavoritesRepository
import cz.euroklicmapa.data.mapper.toPickupPointEntity
import cz.euroklicmapa.data.mapper.toWcLocationEntity
import cz.euroklicmapa.data.repository.VoteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

sealed interface DetailState {
    data object Loading : DetailState
    data class WcDetail(val wc: WcLocationEntity) : DetailState
    data class PickupDetail(val pp: PickupPointEntity) : DetailState
    data object Error : DetailState
}

class DetailViewModel(
    private val repository: EuroklicRepository,
    locationRepository: LocationRepository,
    private val favorites: FavoritesRepository,
    private val addPlaceRepository: AddPlaceRepository,
    private val id: Int,
    private val type: String,
) : ViewModel() {

    private val isPickup = type == "PICKUP"

    val isFavorite: StateFlow<Boolean> = favorites.observeIsFavorite(id, isPickup)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleFavorite() {
        viewModelScope.launch {
            if (isFavorite.value) {
                favorites.remove(id, isPickup)
            } else {
                when (val s = state.value) {
                    is DetailState.WcDetail -> favorites.add(s.wc)
                    is DetailState.PickupDetail -> favorites.add(s.pp)
                    else -> {}
                }
            }
        }
    }

    // WC detail observes Room so a successful vote (which writes back the new counts) reflects
    // immediately here and on the map/list. When the live row is absent (feed scoped it out,
    // removed server-side, never synced) but the place is favourited, fall back to the stored
    // full-detail snapshot so it still opens offline. Pickup detail is a one-shot lookup with
    // the same snapshot fallback.
    val state: StateFlow<DetailState> =
        if (type == "WC") {
            combine(
                repository.observeLocation(id),
                favorites.observeFavorite(id, false),
            ) { live, fav ->
                when {
                    live != null -> DetailState.WcDetail(live)
                    fav != null -> DetailState.WcDetail(fav.toWcLocationEntity())
                    else -> DetailState.Error
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailState.Loading)
        } else {
            combine(
                flow { emit(repository.getPickupPoint(id)) },
                favorites.observeFavorite(id, true),
            ) { live, fav ->
                when {
                    live != null -> DetailState.PickupDetail(live)
                    fav != null -> DetailState.PickupDetail(fav.toPickupPointEntity())
                    else -> DetailState.Error
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailState.Loading)
        }

    val userLocation: StateFlow<GeoPoint?> = locationRepository.lastKnown

    private val _voting = MutableStateFlow(false)
    val voting: StateFlow<Boolean> = _voting.asStateFlow()

    /** This session's own vote, for button highlight. Not persisted. */
    private val _myVote = MutableStateFlow<Boolean?>(null)
    val myVote: StateFlow<Boolean?> = _myVote.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _photoUploading = MutableStateFlow(false)
    val photoUploading: StateFlow<Boolean> = _photoUploading.asStateFlow()

    /** Community notes; null while loading, empty when none / on any failure. WC only. */
    private val _comments = MutableStateFlow<List<WcComment>?>(null)
    val comments: StateFlow<List<WcComment>?> = _comments.asStateFlow()

    init {
        locationRepository.refresh()
        if (type == "WC") loadComments()
    }

    private fun loadComments() {
        viewModelScope.launch { _comments.value = repository.getComments(id) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Upload a user-supplied photo for this place. Goes through the moderation queue server-side. */
    fun uploadPhoto(uri: Uri) {
        if (_photoUploading.value || type != "WC") return
        viewModelScope.launch {
            _photoUploading.value = true
            _message.value = when (val r = addPlaceRepository.submitPhoto(id, uri)) {
                AddPhotoResult.Success -> "Fotka odeslána ke schválení."
                is AddPhotoResult.Error -> r.message
            }
            _photoUploading.value = false
        }
    }

    fun vote(like: Boolean) {
        if (_voting.value || type != "WC") return
        viewModelScope.launch {
            _voting.value = true
            val previous = _myVote.value
            _myVote.value = like
            when (val outcome = repository.vote(id, like)) {
                is VoteOutcome.Success ->
                    _message.value = if (like) "Díky, zaznamenáno jako funguje." else "Díky, zaznamenáno."
                is VoteOutcome.Rejected ->
                    _message.value = outcome.message // e.g. "Už jste takto hlasovali." — keep highlight
                VoteOutcome.Failed -> {
                    _myVote.value = previous
                    _message.value = "Hlas se teď nepodařilo odeslat. Zkuste to znovu."
                }
            }
            _voting.value = false
        }
    }
}
