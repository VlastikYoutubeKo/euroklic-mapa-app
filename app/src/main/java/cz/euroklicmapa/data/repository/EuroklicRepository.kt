package cz.euroklicmapa.data.repository

import android.util.Log
import cz.euroklicmapa.data.local.EuroklicDao
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.SyncMetadataEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.location.LocationRepository
import cz.euroklicmapa.data.mapper.toEntity
import cz.euroklicmapa.data.model.ApiResult
import cz.euroklicmapa.data.model.PostCommentRequest
import cz.euroklicmapa.data.model.WcComment
import cz.euroklicmapa.data.remote.EuroklicApi
import cz.euroklicmapa.util.nowUtcTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

object SyncKeys {
    const val LOCATIONS = "locations"
    const val PICKUP_POINTS = "pickup_points"
}

sealed interface PostCommentResult {
    data object Success : PostCommentResult
    data class Error(val message: String) : PostCommentResult
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
    /** One-shot ids of every cached WC (no refresh side effect) — for the notification poll worker. */
    suspend fun getAllLocationIds(): List<Int>
    /** One-shot fetch of the cached WC rows for [ids] (no refresh side effect). */
    suspend fun getLocationsByIds(ids: List<Int>): List<WcLocationEntity>
    suspend fun refreshLocations()
    suspend fun refreshPickupPoints()
    /** Anonymous vote via `/api_csrf.php` + `/api_vote.php`. Updates the Room row on success. */
    suspend fun vote(locationId: Int, like: Boolean): VoteOutcome

    /** Community notes (`GET /api_comments.php`, approved only). Empty list on any failure. */
    suspend fun getComments(locationId: Int): List<WcComment>

    /**
     * Post a comment (`POST /api_comments.php`, Bearer). Server-side it goes to a moderation
     * queue — success means "queued", not "visible". Maps HTTP codes to Czech messages.
     */
    suspend fun postComment(locationId: Int, text: String): PostCommentResult
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

    override suspend fun getAllLocationIds(): List<Int> = dao.getAllLocationIds()

    override suspend fun getLocationsByIds(ids: List<Int>): List<WcLocationEntity> =
        if (ids.isEmpty()) emptyList() else dao.getLocationsByIds(ids)

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

    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Best-effort pull of the server's own message out of a non-2xx JSON error body. */
    private fun serverMessage(e: HttpException): String? = try {
        e.response()?.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { errorJson.decodeFromString<ApiResult>(it) }
            ?.let { it.error ?: it.message }
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    override suspend fun postComment(locationId: Int, text: String): PostCommentResult = try {
        val resp = api.postComment(PostCommentRequest(locationId, text.trim()))
        when {
            resp.success -> PostCommentResult.Success
            !resp.error.isNullOrBlank() -> PostCommentResult.Error(resp.error)
            !resp.message.isNullOrBlank() -> PostCommentResult.Error(resp.message)
            else -> PostCommentResult.Error("Komentář se nepodařilo odeslat.")
        }
    } catch (e: HttpException) {
        PostCommentResult.Error(
            when (e.code()) {
                401, 403 -> "Přihlášení vypršelo. Přihlaste se prosím znovu."
                400 -> serverMessage(e)
                    ?: "Komentář se nepodařilo odeslat (zkontrolujte délku 3–2000 znaků, max 1 odkaz)."
                404 -> "Tohle místo se nepodařilo najít."
                409 -> "Tenhle komentář jste už přidali."
                429 -> "Za poslední dobu jste přidali hodně příspěvků. Zkuste to později."
                else -> "Chyba serveru (${e.code()})."
            },
        )
    } catch (e: SerializationException) {
        PostCommentResult.Error("Odpověď serveru se nepodařilo zpracovat.")
    } catch (e: Exception) {
        PostCommentResult.Error("Bez připojení. Zkuste to znovu, až budete online.")
    }
}
