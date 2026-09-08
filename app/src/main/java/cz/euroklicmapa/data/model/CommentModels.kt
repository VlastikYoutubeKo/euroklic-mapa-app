package cz.euroklicmapa.data.model

import kotlinx.serialization.Serializable

/**
 * One community note on a place, as returned by `GET /api_comments.php?location_id=…`
 * (verified live 2026-09-05: `author` falls back to `"Anonym"`, `created_at` is
 * `"YYYY-MM-DD HH:MM:SS"`). Currently read-only in the app — the website is the only
 * place that submits them.
 */
@Serializable
data class WcComment(
    val text: String = "",
    val author: String = "Anonym",
    val created_at: String? = null,
)

/** HTTP 200 even on error (`{"status":"error","message":"Invalid location_id"}`) — check [status]. */
@Serializable
data class CommentsResponse(
    val status: String? = null,
    val message: String? = null,
    val comments: List<WcComment> = emptyList(),
)

/**
 * `POST /api_comments.php` body (JSON, not multipart). Bearer required (attached by
 * `AuthInterceptor`); the author is taken from the token — do not send a nickname.
 * Never publishes directly — the row lands in a moderation queue (`status='pending'`).
 * Response reuses [cz.euroklicmapa.data.model.ApiResult] (`success`/`error`/`message`; the
 * always-present `status` key is ignored by `Json { ignoreUnknownKeys = true }`).
 */
@Serializable
data class PostCommentRequest(val location_id: Int, val text: String)
