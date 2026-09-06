package cz.euroklicmapa.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption of the auth token, keyed by a non-exportable `AndroidKeyStore` key.
 * Same idea as `EncryptedSharedPreferences`, hand-rolled because
 * `androidx.security:security-crypto` isn't on the offline classpath. The Keystore key is
 * device-bound and never leaves the secure hardware / TEE, so the ciphertext at rest is useless
 * off-device (rooted extraction, ADB/cloud backup restore to another phone).
 */
class SecureTokenStore {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** base64( iv ‖ ciphertext ‖ gcmTag ), or null if encryption fails. */
    fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val blob = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(blob, Base64.NO_WRAP)
    }.onFailure { Log.w("SecureTokenStore", "encrypt failed", it) }.getOrNull()

    /** Plaintext, or null if the blob is corrupt / the key is gone (e.g. restored to a new device). */
    fun decrypt(stored: String): String? = runCatching {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, GCM_IV_LEN)
        val body = blob.copyOfRange(GCM_IV_LEN, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.onFailure { Log.w("SecureTokenStore", "decrypt failed", it) }.getOrNull()

    /** Drop the key — forces a fresh one and a re-login. */
    fun reset() {
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "euroklic_auth_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LEN = 12
        const val GCM_TAG_BITS = 128
    }
}
