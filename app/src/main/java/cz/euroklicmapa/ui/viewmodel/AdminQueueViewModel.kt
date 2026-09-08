package cz.euroklicmapa.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.euroklicmapa.data.repository.AdminActionResult
import cz.euroklicmapa.data.repository.AdminListState
import cz.euroklicmapa.data.repository.AdminRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AdminQueueViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _state = MutableStateFlow<AdminListState>(AdminListState.Loading)
    val state: StateFlow<AdminListState> = _state.asStateFlow()

    private val _snack = Channel<String>(Channel.BUFFERED)
    val snack = _snack.receiveAsFlow()

    var actingId by mutableStateOf<Int?>(null)
        private set

    var actingPhotoId by mutableStateOf<Int?>(null)
        private set

    var actingCommentId by mutableStateOf<Int?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = AdminListState.Loading
            _state.value = adminRepository.loadPending()
        }
    }

    fun review(id: Int, approve: Boolean) {
        if (actingId != null) return
        actingId = id
        viewModelScope.launch {
            when (val result = adminRepository.review(id, approve)) {
                is AdminActionResult.Ok -> {
                    _snack.trySend(result.message)
                    (_state.value as? AdminListState.Loaded)?.let { loaded ->
                        _state.value = loaded.copy(pending = loaded.pending.filterNot { it.id == id })
                    }
                }
                is AdminActionResult.Failed -> _snack.trySend(result.message)
            }
            actingId = null
        }
    }

    fun reviewPhoto(photoId: Int, approve: Boolean) {
        if (actingPhotoId != null) return
        actingPhotoId = photoId
        viewModelScope.launch {
            when (val result = adminRepository.reviewPhoto(photoId, approve)) {
                is AdminActionResult.Ok -> {
                    _snack.trySend(result.message)
                    (_state.value as? AdminListState.Loaded)?.let { loaded ->
                        _state.value = loaded.copy(photos = loaded.photos.filterNot { it.id == photoId })
                    }
                }
                is AdminActionResult.Failed -> _snack.trySend(result.message)
            }
            actingPhotoId = null
        }
    }

    fun reviewComment(commentId: Int, approve: Boolean) {
        if (actingCommentId != null) return
        actingCommentId = commentId
        viewModelScope.launch {
            when (val result = adminRepository.reviewComment(commentId, approve)) {
                is AdminActionResult.Ok -> {
                    _snack.trySend(result.message)
                    (_state.value as? AdminListState.Loaded)?.let { loaded ->
                        _state.value = loaded.copy(comments = loaded.comments.filterNot { it.id == commentId })
                    }
                }
                is AdminActionResult.Failed -> _snack.trySend(result.message)
            }
            actingCommentId = null
        }
    }
}

class AdminQueueViewModelFactory(
    private val adminRepository: AdminRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AdminQueueViewModel::class.java)) { "Unknown ViewModel class" }
        @Suppress("UNCHECKED_CAST")
        return AdminQueueViewModel(adminRepository) as T
    }
}
