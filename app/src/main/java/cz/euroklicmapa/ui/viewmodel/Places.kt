package cz.euroklicmapa.ui.viewmodel

import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.util.PlaceStatus
import cz.euroklicmapa.util.placeStatus
import org.osmdroid.util.GeoPoint

/** Everything a list row (Seznam, Oblíbené, or the Map sheet) needs, flattened from the two feeds. */
data class PlaceListItem(
    val id: Int,
    val isPickup: Boolean,
    val title: String,
    val subtitle: String?,
    val position: GeoPoint,
    val source: String?,
    val status: PlaceStatus?,
    val country: String?,
    val likes: Int?,
    val dislikes: Int?,
    val distanceMeters: Double?,
) {
    val navType: String get() = if (isPickup) "PICKUP" else "WC"
}

enum class PlaceCategory { ALL, TOILET, PICKUP }

/**
 * Merge the WC + pickup feeds into one distance-sorted list, honouring the category filter.
 * Shared by [ListViewModel] and [MapViewModel] so the Seznam tab and the Map sheet can never
 * drift apart. Client-side over the already-cached data — offline-first.
 */
fun flattenPlaces(
    locations: List<WcLocationEntity>,
    pickupPoints: List<PickupPointEntity>,
    userLocation: GeoPoint?,
    category: PlaceCategory,
): List<PlaceListItem> {
    val wc = if (category == PlaceCategory.PICKUP) emptyList() else locations.map {
        val pos = GeoPoint(it.latitude, it.longitude)
        PlaceListItem(
            id = it.id,
            isPickup = false,
            title = it.name.ifBlank { "Bezbariérové WC" },
            subtitle = null,
            position = pos,
            source = it.source,
            status = placeStatus(it.source, it.likes, it.dislikes, it.lastVerified),
            country = it.country,
            likes = it.likes,
            dislikes = it.dislikes,
            distanceMeters = userLocation?.let { u -> pos.distanceToAsDouble(u) },
        )
    }
    val pp = if (category == PlaceCategory.TOILET) emptyList() else pickupPoints.map {
        val pos = GeoPoint(it.latitude, it.longitude)
        PlaceListItem(
            id = it.id,
            isPickup = true,
            title = it.orgName.ifBlank { "Výdejní místo Euroklíče" },
            subtitle = it.address ?: listOfNotNull(it.district, it.kraj).joinToString(", ").ifBlank { null },
            position = pos,
            source = null,
            status = null,
            country = null,
            likes = null,
            dislikes = null,
            distanceMeters = userLocation?.let { u -> pos.distanceToAsDouble(u) },
        )
    }
    val all = wc + pp
    return if (userLocation != null) all.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
    else all.sortedBy { it.title.lowercase() }
}

/** The 4-value [PlaceStatus] (marker/badge rendering, mirrors the website) collapses to 3
 *  filter buckets here — OFFICIAL and RECENTLY_VERIFIED both just mean "verified" to a filter. */
enum class StatusFilter { VERIFIED, UNVERIFIED, REPORTED }

private fun PlaceStatus.toFilterBucket(): StatusFilter = when (this) {
    PlaceStatus.REPORTED -> StatusFilter.REPORTED
    PlaceStatus.OFFICIAL, PlaceStatus.RECENTLY_VERIFIED -> StatusFilter.VERIFIED
    PlaceStatus.UNVERIFIED -> StatusFilter.UNVERIFIED
}

/**
 * Extra filters on top of [flattenPlaces]'s category split — "2.0" spec §24/28. Kept as a
 * separate pass (not folded into `flattenPlaces` itself) so the 3 existing callers are
 * unaffected unless they opt in. Empty [statuses] = no status filter. `null` [maxDistanceMeters]
 * = no distance cap. Pickup points carry no [PlaceListItem.status] — a status filter never hides
 * them (they don't participate in "verification" at all), but distance still applies to both.
 */
data class PlaceFilters(
    val statuses: Set<StatusFilter> = emptySet(),
    val maxDistanceMeters: Double? = null,
) {
    val activeCount: Int get() = statuses.size + if (maxDistanceMeters != null) 1 else 0
}

fun applyPlaceFilters(items: List<PlaceListItem>, filters: PlaceFilters): List<PlaceListItem> {
    var result = items
    if (filters.statuses.isNotEmpty()) {
        result = result.filter { it.status == null || it.status.toFilterBucket() in filters.statuses }
    }
    val cap = filters.maxDistanceMeters
    if (cap != null) {
        result = result.filter { (it.distanceMeters ?: Double.MAX_VALUE) <= cap }
    }
    return result
}
