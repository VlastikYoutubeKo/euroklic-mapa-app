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
