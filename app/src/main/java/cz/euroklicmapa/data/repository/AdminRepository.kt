package cz.euroklicmapa.data.repository

import cz.euroklicmapa.data.model.AdminPlace
import cz.euroklicmapa.data.model.ApiResult
import cz.euroklicmapa.data.model.CommentSuggestion
import cz.euroklicmapa.data.model.PhotoSuggestion
import cz.euroklicmapa.data.remote.EuroklicApi
import retrofit2.HttpException

sealed interface AdminListState {
    data object Loading : AdminListState
    data class Loaded(
        val pending: List<AdminPlace>,
        val photos: List<PhotoSuggestion> = emptyList(),
        val comments: List<CommentSuggestion> = emptyList(),
    ) : AdminListState
    data class Error(val message: String) : AdminListState
}

sealed interface AdminActionResult {
    data class Ok(val message: String) : AdminActionResult
    data class Failed(val message: String) : AdminActionResult
}

class AdminRepository(private val api: EuroklicApi) {

    suspend fun loadPending(): AdminListState = try {
        val r = api.adminList()
        AdminListState.Loaded(r.locations, r.photo_suggestions, r.comment_suggestions)
    } catch (e: HttpException) {
        AdminListState.Error(if (e.code() == 403) "Nemáte oprávnění." else "Chyba serveru (${e.code()}).")
    } catch (e: Exception) {
        AdminListState.Error("Bez připojení.")
    }

    suspend fun review(id: Int, approve: Boolean): AdminActionResult = act(approve) {
        api.adminReview(action = if (approve) "approve" else "reject", id = id)
    }

    /** Photo suggestions use `approve_photo`/`reject_photo` + `photo_id` (separate id space). */
    suspend fun reviewPhoto(photoId: Int, approve: Boolean): AdminActionResult = act(approve) {
        api.adminReview(action = if (approve) "approve_photo" else "reject_photo", photoId = photoId)
    }

    /** Comment suggestions use `approve_comment`/`reject_comment` + `comment_id` (separate id space). */
    suspend fun reviewComment(commentId: Int, approve: Boolean): AdminActionResult = act(approve) {
        api.adminReview(action = if (approve) "approve_comment" else "reject_comment", commentId = commentId)
    }

    private suspend fun act(approve: Boolean, call: suspend () -> ApiResult): AdminActionResult = try {
        val resp = call()
        if (resp.success) {
            AdminActionResult.Ok(if (approve) "Schváleno." else "Zamítnuto.")
        } else {
            AdminActionResult.Failed(resp.error ?: resp.message ?: "Akce se nezdařila.")
        }
    } catch (e: HttpException) {
        AdminActionResult.Failed(if (e.code() == 403) "Nemáte oprávnění." else "Chyba serveru (${e.code()}).")
    } catch (e: Exception) {
        AdminActionResult.Failed("Bez připojení.")
    }
}
