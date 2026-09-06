package cz.euroklicmapa.data.repository

import cz.euroklicmapa.data.local.EuroklicDao
import cz.euroklicmapa.data.local.FavoriteEntity
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val dao: EuroklicDao) {

    fun observeAll(): Flow<List<FavoriteEntity>> = dao.observeFavorites()

    fun observeIsFavorite(id: Int, isPickup: Boolean): Flow<Boolean> =
        dao.observeIsFavorite(id, isPickup)

    suspend fun add(wc: WcLocationEntity) = dao.insertFavorite(
        FavoriteEntity(
            placeId = wc.id,
            isPickup = false,
            title = wc.name,
            source = wc.source,
            lastVerified = wc.lastVerified,
            likes = wc.likes,
            dislikes = wc.dislikes,
            longitude = wc.longitude,
            latitude = wc.latitude,
            savedAt = System.currentTimeMillis(),
        ),
    )

    suspend fun add(pp: PickupPointEntity) = dao.insertFavorite(
        FavoriteEntity(
            placeId = pp.id,
            isPickup = true,
            title = pp.orgName,
            source = null,
            lastVerified = null,
            likes = 0,
            dislikes = 0,
            longitude = pp.longitude,
            latitude = pp.latitude,
            savedAt = System.currentTimeMillis(),
        ),
    )

    suspend fun remove(id: Int, isPickup: Boolean) = dao.deleteFavorite(id, isPickup)
}
