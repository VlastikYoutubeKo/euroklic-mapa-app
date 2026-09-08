package cz.euroklicmapa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class WcLocationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val source: String,
    val description: String?,
    val note: String?,
    val photoUrl: String?,
    val likes: Int,
    val dislikes: Int,
    val lastVerified: String?,
    val webUrl: String?,
    val openingHours: String?,
    val wcOpeningHours: String?,
    val access: String?,
    val wheelchair: String?,
    val accessibilityNote: String?,
    val country: String?,
    val floorPlanUrl: String?,
    val longitude: Double,
    val latitude: Double
)

@Entity(tableName = "pickup_points")
data class PickupPointEntity(
    @PrimaryKey val id: Int,
    val kraj: String?,
    val district: String?,
    val orgName: String,
    val address: String?,
    val phone: String?,
    val email: String?,
    val hours: String?,
    val note: String?,
    val precision: String?,
    val sourceUrl: String?,
    val longitude: Double,
    val latitude: Double
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val lastSyncTimestamp: Long
)

/**
 * A saved place. A **full detail snapshot** (DB v8) so Favourites open with the complete
 * `DetailScreen` body — even offline and even if the place fell out of the feed cache, was
 * removed server-side, or was never synced on this device. `DetailViewModel` reconstructs a
 * [WcLocationEntity] / [PickupPointEntity] from this row when the live Room row is absent.
 *
 * Everything past [savedAt] is a nullable snapshot column: WC-side and pickup-side fields share
 * this one table ([isPickup] says which set is meaningful), [note] is reused by both.
 */
@Entity(tableName = "favorites", primaryKeys = ["placeId", "isPickup"])
data class FavoriteEntity(
    val placeId: Int,
    val isPickup: Boolean,
    val title: String,
    val source: String?,
    val lastVerified: String?,
    val likes: Int,
    val dislikes: Int,
    val longitude: Double,
    val latitude: Double,
    val savedAt: Long,
    // ---- WC snapshot (DetailScreen.WcBody) ----
    val description: String? = null,
    val note: String? = null,
    val photoUrl: String? = null,
    val webUrl: String? = null,
    val openingHours: String? = null,
    val wcOpeningHours: String? = null,
    val access: String? = null,
    val wheelchair: String? = null,
    val accessibilityNote: String? = null,
    val country: String? = null,
    val floorPlanUrl: String? = null,
    // ---- pickup snapshot (DetailScreen.PickupBody) ----
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val hours: String? = null,
    val district: String? = null,
    val kraj: String? = null,
    val precision: String? = null,
    val sourceUrl: String? = null,
)
