package cz.euroklicmapa.data.model

import kotlinx.serialization.Serializable

/**
 * Flattened union of toilet + pickup properties from `/api_app_places.php`. `category` is
 * `"toilet"` or `"pickup"`; toilet features also carry `source_group`
 * (`"oficialni"` / `"komunitni"`). Every non-key field is nullable/defaulted so one shape
 * can deserialise both.
 */
@Serializable
data class AppPlaceProperties(
    val id: Int,
    val category: String = "",
    // toilet
    val name: String? = null,
    val source: String? = null,
    val source_group: String? = null,
    val description: String? = null,
    val note: String? = null,
    val amenity: String? = null,
    val photo_url: String? = null,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val last_verified: String? = null,
    // pickup
    val kraj: String? = null,
    val district: String? = null,
    val org_name: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val hours: String? = null,
    val precision: String? = null,
    val source_url: String? = null,
)
