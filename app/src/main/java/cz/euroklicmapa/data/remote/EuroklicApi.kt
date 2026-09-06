package cz.euroklicmapa.data.remote

import cz.euroklicmapa.data.model.AdminListResponse
import cz.euroklicmapa.data.model.ApiResult
import cz.euroklicmapa.data.model.AppPlaceProperties
import cz.euroklicmapa.data.model.CommentsResponse
import cz.euroklicmapa.data.model.FeatureCollection
import cz.euroklicmapa.data.model.MeResponse
import cz.euroklicmapa.data.model.PickupPointProperties
import cz.euroklicmapa.data.model.TokenResponse
import cz.euroklicmapa.data.model.WcProperties
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface EuroklicApi {
    /** `near` = `"lat,lon"`, `radiusKm` 1–500 (default 50 server-side). Omit both for the full feed. */
    @GET("api_locations.php")
    suspend fun getLocations(
        @Query("near") near: String? = null,
        @Query("radius_km") radiusKm: Int? = null,
    ): FeatureCollection<WcProperties>

    @GET("api_pickup_points.php")
    suspend fun getPickupPoints(
        @Query("near") near: String? = null,
        @Query("radius_km") radiusKm: Int? = null,
    ): FeatureCollection<PickupPointProperties>

    /**
     * Unified list feed. `category` ∈ `toilet | pickup` or null for both. Currently unused —
     * the Seznam filters the already-cached merged data client-side (offline-first; the map
     * needs both feeds cached anyway). Wire this in if server-side filtering is ever wanted.
     */
    @GET("api_app_places.php")
    suspend fun appPlaces(@Query("category") category: String? = null): FeatureCollection<AppPlaceProperties>

    /** Hands back a CSRF token bound to the anonymous PHP session (needs a persistent CookieJar). */
    @GET("api_csrf.php")
    suspend fun csrf(): CsrfResponse

    /** `type` is `"like"` or `"dislike"`. Deduped by IP server-side. */
    @FormUrlEncoded
    @POST("api_vote.php")
    suspend fun vote(
        @Field("id") id: Int,
        @Field("type") type: String,
        @Header("X-CSRF-Token") token: String,
    ): VoteResponse

    /** PKCE: exchange the one-time `code` (from the auth callback) + `code_verifier` for a Bearer token. */
    @FormUrlEncoded
    @POST("api_token.php")
    suspend fun exchangeToken(
        @Field("code") code: String,
        @Field("code_verifier") verifier: String,
    ): TokenResponse

    /** Bearer (added by `AuthInterceptor`). Also usable unauthed → `{ logged_in: false }`. */
    @GET("api_me.php")
    suspend fun me(): MeResponse

    /** Revokes the current Bearer token server-side. */
    @POST("api_logout.php")
    suspend fun logout(): ApiResult

    /**
     * Submit a new place (Bearer auth; no CSRF needed with a token). `photo` part name is
     * `photo_file`. Server fills `source='user'`, `approved=0`, `country`, author from auth.
     */
    @Multipart
    @POST("api_add.php")
    suspend fun addPlace(
        @Part("name") name: RequestBody,
        @Part("desc") desc: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part photo: MultipartBody.Part?,
    ): ApiResult

    /**
     * Attach a photo to an EXISTING, approved place (Bearer). `id` = `locations.id`, `photo`
     * part name is `photo_file` (same as `api_add.php`). Server queues it as a
     * `photo_suggestions` row (`status='pending'`) — an admin approves it (`api_admin.php`
     * `action=approve_photo`) before it becomes the place's `photo_url`. 403 no token, 404
     * unknown/unapproved id, 413 over 8 MB, 429 rate-limited (10/h/user).
     */
    @Multipart
    @POST("api_add_photo.php")
    suspend fun addPhoto(
        @Part("id") id: RequestBody,
        @Part photo: MultipartBody.Part,
    ): ApiResult

    /** Admin (Bearer). No param → the `approved=0` queue; `id` → one row; `q` → search. */
    @GET("api_admin_list.php")
    suspend fun adminList(
        @Query("id") id: Int? = null,
        @Query("q") query: String? = null,
    ): AdminListResponse

    /**
     * Admin (Bearer). New places: `action` ∈ `approve | reject` + `id`. Photo suggestions:
     * `action` ∈ `approve_photo | reject_photo` + `photoId` (`id` unused, kept separate so the
     * two id spaces don't mix).
     */
    @FormUrlEncoded
    @POST("api_admin.php")
    suspend fun adminReview(
        @Field("action") action: String,
        @Field("id") id: Int? = null,
        @Field("photo_id") photoId: Int? = null,
    ): ApiResult

    /**
     * Read-only community notes on one place (auth-free). HTTP 200 even on error — check
     * [CommentsResponse.status]. A missing path serves homepage HTML with 200, which the
     * kotlinx.serialization converter turns into an exception; the repository maps that to
     * an empty list.
     */
    @GET("api_comments.php")
    suspend fun comments(@Query("location_id") locationId: Int): CommentsResponse

    companion object {
        const val BASE_URL = "https://euroklic.odjezdy.online/"
    }
}
