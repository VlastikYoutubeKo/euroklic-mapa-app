package cz.euroklicmapa.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CsrfResponse(val csrf: String = "")

/** `/api_vote.php` — HTTP 200 even on failure; check [success]. */
@Serializable
data class VoteResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val likes: Int? = null,
    val dislikes: Int? = null,
)
