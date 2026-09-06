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
 * A saved place. Denormalised so Favourites render (and stay usable) even if the main cache is
 * cleared or the place drops out of the feed.
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
)
