package cz.euroklicmapa.data.auth

/**
 * Synchronous token access for the OkHttp interceptor (which runs off the main thread and can't
 * suspend on DataStore). [AuthRepository] is the only writer; it mirrors the persisted token here.
 */
class AuthTokenHolder {
    @Volatile
    var token: String? = null
}
