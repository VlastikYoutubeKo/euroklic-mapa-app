package cz.euroklicmapa.data.repository

import android.content.Context
import android.net.Uri
import cz.euroklicmapa.data.remote.EuroklicApi
import cz.euroklicmapa.util.downscaleToJpeg
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.util.Locale

sealed interface AddPlaceResult {
    data class Success(val id: Int?) : AddPlaceResult
    data class Error(val message: String) : AddPlaceResult
}

sealed interface AddPhotoResult {
    data object Success : AddPhotoResult
    data class Error(val message: String) : AddPhotoResult
}

class AddPlaceRepository(
    private val api: EuroklicApi,
    private val appContext: Context,
) {
    /** Bearer auth is added by the OkHttp interceptor. Photo is downscaled before upload. */
    suspend fun submit(
        name: String,
        description: String,
        lat: Double,
        lon: Double,
        photo: Uri?,
    ): AddPlaceResult {
        val text = "text/plain".toMediaType()
        val photoPart = photo?.let { uri ->
            val bytes = downscaleToJpeg(appContext, uri) ?: return AddPlaceResult.Error("Fotku se nepodařilo zpracovat.")
            MultipartBody.Part.createFormData(
                "photo_file",
                "photo.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType()),
            )
        }
        return try {
            val resp = api.addPlace(
                name = name.trim().toRequestBody(text),
                desc = description.trim().toRequestBody(text),
                lat = "%.6f".format(Locale.US, lat).toRequestBody(text),
                lon = "%.6f".format(Locale.US, lon).toRequestBody(text),
                photo = photoPart,
            )
            when {
                resp.success -> AddPlaceResult.Success(resp.id)
                !resp.error.isNullOrBlank() -> AddPlaceResult.Error(resp.error)
                !resp.message.isNullOrBlank() -> AddPlaceResult.Error(resp.message)
                else -> AddPlaceResult.Error("Odeslání se nezdařilo.")
            }
        } catch (e: HttpException) {
            AddPlaceResult.Error(
                when (e.code()) {
                    401 -> "Přihlášení vypršelo. Přihlaste se prosím znovu."
                    413 -> "Fotka je příliš velká."
                    else -> "Chyba serveru (${e.code()})."
                },
            )
        } catch (e: Exception) {
            AddPlaceResult.Error("Bez připojení. Zkuste to znovu, až budete online.")
        }
    }

    /**
     * Attach a photo to an existing, approved place (id). Downscales client-side, then
     * multipart-uploads to `api_add_photo.php` (Bearer, `id` + `photo_file`). Server queues it
     * as a `photo_suggestions` row (`status='pending'`) — an admin approves it before it becomes
     * the place's `photo_url`. HTTP codes: 403 no/expired token, 404 place not found or not
     * approved, 413 over 8 MB, 429 rate-limited (10/h per user).
     */
    suspend fun submitPhoto(placeId: Int, photo: Uri): AddPhotoResult {
        val bytes = downscaleToJpeg(appContext, photo)
            ?: return AddPhotoResult.Error("Fotku se nepodařilo zpracovat.")
        val part = MultipartBody.Part.createFormData(
            "photo_file",
            "photo.jpg",
            bytes.toRequestBody("image/jpeg".toMediaType()),
        )
        return try {
            val resp = api.addPhoto(
                id = placeId.toString().toRequestBody("text/plain".toMediaType()),
                photo = part,
            )
            when {
                resp.success -> AddPhotoResult.Success
                !resp.error.isNullOrBlank() -> AddPhotoResult.Error(resp.error)
                !resp.message.isNullOrBlank() -> AddPhotoResult.Error(resp.message)
                else -> AddPhotoResult.Error("Odeslání se nezdařilo.")
            }
        } catch (e: SerializationException) {
            AddPhotoResult.Error("Odpověď serveru se nepodařilo zpracovat.")
        } catch (e: HttpException) {
            AddPhotoResult.Error(
                when (e.code()) {
                    401, 403 -> "Přihlášení vypršelo. Přihlaste se prosím znovu."
                    404 -> "Tohle místo se nepodařilo najít."
                    413 -> "Fotka je příliš velká (max 8 MB)."
                    429 -> "Za poslední hodinu jste přidali hodně fotek. Zkuste to za chvíli."
                    else -> "Chyba serveru (${e.code()})."
                },
            )
        } catch (e: Exception) {
            AddPhotoResult.Error("Bez připojení. Zkuste to znovu, až budete online.")
        }
    }
}
