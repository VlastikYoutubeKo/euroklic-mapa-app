package cz.euroklicmapa.data.repository

import cz.euroklicmapa.data.local.EuroklicDao
import cz.euroklicmapa.data.local.FavoriteEntity
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.mapper.toFavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoritesRepository(private val dao: EuroklicDao) {

    fun observeAll(): Flow<List<FavoriteEntity>> = dao.observeFavorites()

    fun observeIsFavorite(id: Int, isPickup: Boolean): Flow<Boolean> =
        dao.observeIsFavorite(id, isPickup)

    /** Single favourite snapshot — the offline fallback source for `DetailViewModel`. */
    fun observeFavorite(id: Int, isPickup: Boolean): Flow<FavoriteEntity?> =
        dao.observeFavorite(id, isPickup)

    suspend fun add(wc: WcLocationEntity) = dao.insertFavorite(wc.toFavoriteEntity())

    suspend fun add(pp: PickupPointEntity) = dao.insertFavorite(pp.toFavoriteEntity())

    suspend fun remove(id: Int, isPickup: Boolean) = dao.deleteFavorite(id, isPickup)
}
