package cz.euroklicmapa.data.repository

import android.util.Log
import cz.euroklicmapa.data.local.EuroklicDao
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.SyncMetadataEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.mapper.toEntity
import cz.euroklicmapa.data.model.WcComment
import cz.euroklicmapa.data.remote.EuroklicApi
import cz.euroklicmapa.util.nowUtcTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import retrofit2.HttpException

object SyncKeys {
    const val LOCATIONS = "locations"
    const val PICKUP_POINTS = "pickup_points"
}

sealed interface VoteOutcome {
    /** New authoritative counts from the server (fall back to optimistic if either is -1). */
    data class Success(val likes: Int, val dislikes: Int) : VoteOutcome
    /** Server said no — e.g. "Už jste takto hlasovali." */
    data class Rejected(val message: String) : VoteOutcome
    data object Failed : VoteOutcome
}

interface EuroklicRepository {
    fun getLocations(): Flow<List<WcLocationEntity>>
    fun getPickupPoints(): Flow<List<PickupPointEntity>>
    /** Epoch millis of the last successful `/api_locations.php` fetch, or null if never. */
    fun observeLocationsSync(): Flow<Long?>
    suspend fun getLocation(id: Int): WcLocationEntity?
    fun observeLocation(id: Int): Flow<WcLocationEntity?>
    suspend fun getPickupPoint(id: Int): PickupPointEntity?
    suspend fun refreshLocations()
    suspend fun refreshPickupPoints()
    /** Anonymous vote via `/api_csrf.php` + `/api_vote.php`. Updates the Room row on success. */
    suspend fun vote(locationId: Int, like: Boolean): VoteOutcome

    /** Read-only community notes (`GET /api_comments.php`). Empty list on any failure. */
    suspend fun getComments(locationId: Int): List<WcComment>
}

class EuroklicRepositoryImpl(
    private val api: EuroklicApi,
    private val dao: EuroklicDao,
    private val locationRepository: LocationRepository,
) : EuroklicRepository {

    /**
     * When we know where the user is, scope the fetch to 500 km — the whole CZ/SK area plus
     * every neighbouring region a road trip reaches, without downloading the global feed.
     * No location → the full feed (still correct, just larger).
     */
    private fun near(): String? = locationRepository.lastKnown.value
        ?.let { "%.5f,%.5f".format(java.util.Locale.US, it.latitude, it.longitude) }

    override fun getLocations(): Flow<List<WcLocationEntity>> =
        dao.getLocations().onStart { refreshLocations() }

    override fun getPickupPoints(): Flow<List<PickupPointEntity>> =
        dao.getPickupPoints().onStart { refreshPickupPoints() }

    override fun observeLocationsSync(): Flow<Long?> =
        dao.observeLastSyncTimestamp(SyncKeys.LOCATIONS)

    override suspend fun getLocation(id: Int): WcLocationEntity? = dao.getLocationById(id)

    override fun observeLocation(id: Int): Flow<WcLocationEntity?> = dao.observeLocationById(id)

    override suspend fun getPickupPoint(id: Int): PickupPointEntity? = dao.getPickupPointById(id)

    override suspend fun refreshLocations() {
        try {
            val n = near()
            var raw = api.getLocations(near = n, radiusKm = n?.let { 500 }).features
            // User is outside the 500 km coverage (e.g. abroad) — fall back to the full feed.
            if (raw.isEmpty() && n != null) raw = api.getLocations().features
            val entities = raw.map { it.toEntity() }
                .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            android.util.Log.i("EuroklicRepo", "locations: fetched ${raw.size}, kept ${entities.size}")
            // Guard against a 200-but-empty response wiping a good cache.
            if (entities.isEmpty()) return
            dao.clearLocations()
            dao.insertLocations(entities)
            dao.updateSyncMetadata(SyncMetadataEntity(SyncKeys.LOCATIONS, System.currentTimeMillis()))
        } catch (e: Exception) {
            // Offline-first: keep serving the cache; the UI surfaces data age from sync_metadata.
            android.util.Log.w("EuroklicRepo", "refreshLocations failed", e)
        }
    }

    override suspend fun refreshPickupPoints() {
        try {
            val n = near()
            var features = api.getPickupPoints(near = n, radiusKm = n?.let { 500 }).features
            if (features.isEmpty() && n != null) features = api.getPickupPoints().features
            val entities = features
                .map { it.toEntity() }
                .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            if (entities.isEmpty()) return
            dao.clearPickupPoints()
            dao.insertPickupPoints(entities)
            dao.updateSyncMetadata(SyncMetadataEntity(SyncKeys.PICKUP_POINTS, System.currentTimeMillis()))
        } catch (e: Exception) {
            android.util.Log.w("EuroklicRepo", "refreshPickupPoints failed", e)
        }
    }

    @Volatile private var csrfToken: String? = null

    private suspend fun ensureCsrf(force: Boolean): String? {
        if (!force) csrfToken?.let { return it }
        return runCatching { api.csrf().csrf.trim().takeIf { it.isNotEmpty() } }
            .getOrNull()
            ?.also { csrfToken = it }
    }

    override suspend fun vote(locationId: Int, like: Boolean): VoteOutcome {
        val type = if (like) "like" else "dislike"
        var forceToken = false
        repeat(2) { attempt ->
            val token = ensureCsrf(forceToken) ?: return VoteOutcome.Failed
            val resp = try {
                api.vote(locationId, type, token)
            } catch (e: HttpException) {
                if (e.code() == 403 && attempt == 0) { forceToken = true; return@repeat }
                android.util.Log.w("EuroklicRepo", "vote HTTP ${e.code()}", e)
                return VoteOutcome.Failed
            } catch (e: Exception) {
                android.util.Log.w("EuroklicRepo", "vote failed", e)
                return VoteOutcome.Failed
            }

            return when {
                resp.success -> {
                    val likes = resp.likes ?: -1
                    val dislikes = resp.dislikes ?: -1
                    if (likes >= 0 && dislikes >= 0) persistVote(locationId, likes, dislikes, like)
                    VoteOutcome.Success(likes, dislikes)
                }
                !resp.message.isNullOrBlank() -> VoteOutcome.Rejected(resp.message)
                !resp.error.isNullOrBlank() -> VoteOutcome.Rejected(resp.error)
                else -> VoteOutcome.Failed
            }
        }
        return VoteOutcome.Failed
    }

    private suspend fun persistVote(locationId: Int, likes: Int, dislikes: Int, upvote: Boolean) {
        val current = dao.getLocationById(locationId) ?: return
        dao.insertLocations(
            listOf(
                current.copy(
                    likes = likes,
                    dislikes = dislikes,
                    lastVerified = if (upvote) nowUtcTimestamp() else current.lastVerified,
                ),
            ),
        )
    }

    override suspend fun getComments(locationId: Int): List<WcComment> = try {
        val resp = api.comments(locationId)
        if (resp.status == "success") resp.comments else emptyList()
    } catch (e: Exception) {
        // Read-only nicety — offline, or a missing endpoint serving homepage HTML with 200
        // (kotlinx turns that into a parse exception). Either way: no comments, not a crash.
        Log.w("EuroklicRepo", "getComments failed", e)
        emptyList()
    }
}
