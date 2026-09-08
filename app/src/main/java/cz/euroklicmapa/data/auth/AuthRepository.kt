package cz.euroklicmapa.data.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.euroklicmapa.data.model.MeResponse
import cz.euroklicmapa.data.remote.EuroklicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface AuthState {
    /** Reading the persisted token / verifying it with `/api_me.php`. */
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val me: MeResponse) : AuthState
}

sealed interface AuthEvent {
    data class SignedIn(val username: String) : AuthEvent
    data class Error(val message: String) : AuthEvent
    data class Info(val message: String) : AuthEvent
}

/** Separate DataStore file from "settings" — a second `preferencesDataStore("settings")` would crash. */
private val Context.authDataStore by preferencesDataStore(name = "auth")
private val TOKEN_KEY = stringPreferencesKey("bearer_token_enc")

/**
 * Token-based auth with a PKCE code exchange (RFC 8252). Login opens the web OAuth flow in the
 * browser (`/auth.php?client=app&state=<nonce>&code_challenge=<S256>`); the backend redirects
 * to `euroklicmapa://auth-callback` **or** the verified App Link
 * `https://euroklic.odjezdy.online/app/auth-callback` with `?code=…&state=…`, which
 * `MainActivity` feeds to [handleCallback]. The app then swaps that one-time `code` (+ the
 * locally-held `code_verifier`) for the real Bearer token at `/api_token.php` — so a leaked
 * redirect URL is useless without the verifier.
 *
 * (Back-compat: if the callback still carries `token=` directly, that path still works until the
 * backend cuts over.) The token is stored **AES-GCM encrypted** ([SecureTokenStore]); it has no
 * expiry but the server may hand back a fresher one via `X-Refreshed-Token`.
 */
class AuthRepository(
    private val appContext: Context,
    private val api: EuroklicApi,
    private val tokenHolder: AuthTokenHolder,
    private val secureStore: SecureTokenStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    @Volatile
    private var pendingState: String? = null

    @Volatile
    private var pendingVerifier: String? = null

    init {
        scope.launch {
            val stored = appContext.authDataStore.data.first()[TOKEN_KEY]
            val token = stored?.let { secureStore.decrypt(it) }
            if (stored != null && token == null) {
                // Ciphertext we can't decrypt — key rotated / restored to a new device. Start clean.
                secureStore.reset()
                appContext.authDataStore.edit { it.remove(TOKEN_KEY) }
            }
            tokenHolder.token = token
            if (token.isNullOrBlank()) _state.value = AuthState.LoggedOut else refreshMe()
        }
    }

    /**
     * Build the login URL, stash the `state` nonce + PKCE verifier we'll need on the callback.
     * Points at the shared hosted login page (`/app-login.php`, live on production since the
     * 2026-09-04 web redesign deploy) — it lists the providers (Discord, Google, …) and forwards
     * `state`/`code_challenge` on to whichever the user picks, so the app never hardcodes a
     * provider list; adding one (e.g. GitHub) is backend-only.
     */
    fun buildLoginUri(): Uri {
        val nonce = randomUrlSafe(16)
        val verifier = randomUrlSafe(48)
        pendingState = nonce
        pendingVerifier = verifier
        return Uri.parse(EuroklicApi.BASE_URL + "app-login.php").buildUpon()
            .appendQueryParameter("client", "app")
            .appendQueryParameter("state", nonce)
            .appendQueryParameter("code_challenge", sha256UrlSafe(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    fun handleCallback(uri: Uri) {
        val returnedState = uri.getQueryParameter("state")
        val expectedState = pendingState
        val verifier = pendingVerifier
        pendingState = null
        pendingVerifier = null

        if (expectedState == null || returnedState != expectedState) {
            _events.tryEmit(AuthEvent.Error("Přihlášení se nezdařilo (neplatný stav)."))
            return
        }
        uri.getQueryParameter("error")?.let {
            Log.w("AuthRepository", "callback error: $it")
            _events.tryEmit(AuthEvent.Error("Přihlášení se nezdařilo."))
            return
        }

        val code = uri.getQueryParameter("code")
        val directToken = uri.getQueryParameter("token")
        when {
            !code.isNullOrBlank() && verifier != null -> scope.launch { exchangeCode(code, verifier) }
            !directToken.isNullOrBlank() -> scope.launch { adoptToken(directToken, announceSignIn = true) }
            else -> _events.tryEmit(AuthEvent.Error("Přihlášení se nezdařilo (chybí kód)."))
        }
    }

    private suspend fun exchangeCode(code: String, verifier: String) {
        val token = try {
            api.exchangeToken(code, verifier).token
        } catch (e: Exception) {
            Log.w("AuthRepository", "token exchange failed", e)
            null
        }
        if (token.isNullOrBlank()) {
            _events.tryEmit(AuthEvent.Error("Přihlášení se nezdařilo (výměna kódu selhala)."))
            return
        }
        adoptToken(token, announceSignIn = true)
    }

    private suspend fun adoptToken(token: String, announceSignIn: Boolean) {
        persist(token)
        refreshMe(announceSignIn = announceSignIn)
    }

    private suspend fun persist(token: String) {
        tokenHolder.token = token
        val enc = secureStore.encrypt(token)
        if (enc != null) {
            appContext.authDataStore.edit { it[TOKEN_KEY] = enc }
        } else {
            Log.w("AuthRepository", "token not persisted (encrypt failed); session is memory-only")
        }
    }

    /** Sliding renewal — the interceptor calls this when the server returns `X-Refreshed-Token`. */
    fun onTokenRefreshed(newToken: String) {
        scope.launch { persist(newToken) }
    }

    suspend fun refreshMe(announceSignIn: Boolean = false) {
        if (tokenHolder.token.isNullOrBlank()) {
            _state.value = AuthState.LoggedOut
            return
        }
        val me = try {
            api.me()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                clearLocal()
                _state.value = AuthState.LoggedOut
                if (announceSignIn) _events.tryEmit(AuthEvent.Error("Přihlášení vypršelo."))
            } else if (_state.value is AuthState.Loading) {
                _state.value = AuthState.LoggedOut
            }
            return
        } catch (e: Exception) {
            Log.w("AuthRepository", "me() failed", e)
            // Offline with a token we can't verify — keep it, retry on next launch.
            if (_state.value is AuthState.Loading) _state.value = AuthState.LoggedOut
            if (announceSignIn) {
                _events.tryEmit(AuthEvent.Error("Přihlášení proběhlo, ale server neodpovídá. Otevřete appku znovu."))
            }
            return
        }

        if (!me.logged_in) {
            clearLocal()
            _state.value = AuthState.LoggedOut
            if (announceSignIn) _events.tryEmit(AuthEvent.Error("Přihlášení vypršelo."))
        } else {
            _state.value = AuthState.LoggedIn(me)
            if (announceSignIn) _events.tryEmit(AuthEvent.SignedIn(me.username.orEmpty()))
        }
    }

    fun logout() {
        scope.launch {
            runCatching { api.logout() } // best effort — revoke locally regardless
            clearLocal()
            _state.value = AuthState.LoggedOut
        }
    }

    /**
     * GDPR: ask the server to delete + anonymise the account, then wipe the local session.
     * Returns true once we're logged out (a 401/403 means the token is already dead → treat as
     * done). Emits an [AuthEvent] either way for the toast.
     */
    suspend fun deleteAccount(): Boolean {
        val ok = try {
            api.deleteAccount().success
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                true // token already invalid server-side
            } else {
                _events.tryEmit(AuthEvent.Error("Smazání účtu se nezdařilo (${e.code()})."))
                return false
            }
        } catch (e: Exception) {
            Log.w("AuthRepository", "deleteAccount failed", e)
            _events.tryEmit(AuthEvent.Error("Bez připojení. Zkuste to prosím znovu."))
            return false
        }
        if (!ok) {
            _events.tryEmit(AuthEvent.Error("Smazání účtu se nezdařilo."))
            return false
        }
        clearLocal()
        _state.value = AuthState.LoggedOut
        _events.tryEmit(AuthEvent.Info("Účet a osobní údaje byly smazány."))
        return true
    }

    /** Invoked from the OkHttp interceptor when an authed request comes back 401. */
    fun onUnauthorized() {
        scope.launch {
            if (_state.value is AuthState.LoggedOut) return@launch
            clearLocal()
            _state.value = AuthState.LoggedOut
            _events.tryEmit(AuthEvent.Error("Přihlášení vypršelo, přihlaste se prosím znovu."))
        }
    }

    private suspend fun clearLocal() {
        tokenHolder.token = null
        appContext.authDataStore.edit { it.remove(TOKEN_KEY) }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun sha256UrlSafe(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
