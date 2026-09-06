package cz.euroklicmapa.data.remote

import cz.euroklicmapa.data.auth.AuthTokenHolder
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every request while a token is held. A 401 on an
 * authed request means the token was revoked server-side — hand that to [onUnauthorized] so the
 * app can drop it and prompt a re-login. (The GeoJSON feeds never 401.)
 *
 * Sliding renewal: if the server decides the token is old it returns a fresh one in
 * `X-Refreshed-Token`; [onTokenRefreshed] persists it and swaps the holder.
 */
class AuthInterceptor(
    private val tokenHolder: AuthTokenHolder,
    private val onUnauthorized: () -> Unit,
    private val onTokenRefreshed: (String) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenHolder.token
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)

        if (response.code == 401 && !token.isNullOrBlank()) {
            onUnauthorized()
            return response
        }
        response.header("X-Refreshed-Token")?.trim()?.takeIf { it.isNotEmpty() && it != token }?.let {
            onTokenRefreshed(it)
        }
        return response
    }
}
