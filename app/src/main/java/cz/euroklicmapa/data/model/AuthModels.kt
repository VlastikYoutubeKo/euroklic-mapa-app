package cz.euroklicmapa.data.model

import kotlinx.serialization.Serializable

/** `POST /api_token.php` — PKCE code → Bearer token exchange. */
@Serializable
data class TokenResponse(
    val token: String? = null,
    val error: String? = null,
)

/** `GET /api_me.php` — `login_provider`/`avatar_url` are null over Bearer for now. */
@Serializable
data class MeResponse(
    val logged_in: Boolean = false,
    val user_id: String? = null,
    val username: String? = null,
    val is_admin: Boolean = false,
    val login_provider: String? = null,
    val avatar_url: String? = null,
)

/** Shape shared by `/api_logout.php`, `/api_add.php`, `/api_admin.php` (HTTP 200 even on failure). */
@Serializable
data class ApiResult(
    val success: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** `/api_add.php` returns the new row id on success. */
    val id: Int? = null,
)

/** `GET /api_admin_list.php` → new places (`locations`) + photo suggestions for approved places. */
@Serializable
data class AdminListResponse(
    val error: String? = null,
    val count: Int = 0,
    val locations: List<AdminPlace> = emptyList(),
    val photo_count: Int = 0,
    val photo_suggestions: List<PhotoSuggestion> = emptyList(),
)

/** One pending `photo_suggestions` row, JOINed with its target place for a preview. */
@Serializable
data class PhotoSuggestion(
    val id: Int,
    val location_id: Int,
    val photo_url: String? = null,
    val author_name: String? = null,
    val status: String? = null,
    val location_name: String? = null,
    val location_lat: Double = 0.0,
    val location_lon: Double = 0.0,
)

@Serializable
data class AdminPlace(
    val id: Int,
    val name: String = "",
    val description: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val photo_url: String? = null,
    val author_name: String? = null,
    val created_at: String? = null,
) {
    val text: String? get() = description?.takeIf { it.isNotBlank() }
}
