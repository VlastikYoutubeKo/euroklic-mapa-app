package cz.euroklicmapa.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EuroklicDao {
    @Query("SELECT * FROM locations")
    fun getLocations(): Flow<List<WcLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<WcLocationEntity>)

    @Query("DELETE FROM locations")
    suspend fun clearLocations()

    @Query("SELECT * FROM pickup_points")
    fun getPickupPoints(): Flow<List<PickupPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPickupPoints(points: List<PickupPointEntity>)

    @Query("DELETE FROM pickup_points")
    suspend fun clearPickupPoints()

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: Int): WcLocationEntity?

    /** One-shot snapshot of every cached WC id — used by the notification poll worker (no Flow). */
    @Query("SELECT id FROM locations")
    suspend fun getAllLocationIds(): List<Int>

    @Query("SELECT * FROM locations WHERE id IN (:ids)")
    suspend fun getLocationsByIds(ids: List<Int>): List<WcLocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun observeLocationById(id: Int): Flow<WcLocationEntity?>

    @Query("SELECT * FROM pickup_points WHERE id = :id")
    suspend fun getPickupPointById(id: Int): PickupPointEntity?

    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSyncTimestamp(key: String): Long?

    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE `key` = :key")
    fun observeLastSyncTimestamp(key: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSyncMetadata(metadata: SyncMetadataEntity)

    // ---- favourites ----

    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE placeId = :id AND isPickup = :isPickup)")
    fun observeIsFavorite(id: Int, isPickup: Boolean): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE placeId = :id AND isPickup = :isPickup")
    suspend fun deleteFavorite(id: Int, isPickup: Boolean)
}
