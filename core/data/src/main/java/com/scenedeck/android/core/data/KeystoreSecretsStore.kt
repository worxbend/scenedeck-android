package com.scenedeck.android.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SecretsStore] backed by a per-app AES/GCM key in AndroidKeyStore; encrypted blobs (never
 * plaintext) are kept in a private SharedPreferences file.
 *
 * Chosen over EncryptedSharedPreferences: androidx.security:security-crypto 1.1.0 deprecates it,
 * and a manual Keystore round-trip is dependency-free (see M2 notes).
 */
class KeystoreSecretsStore(context: Context) : SecretsStore {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val cipher: AesGcmCipher by lazy { AesGcmCipher(getOrCreateKey()) }

    override suspend fun passwordFor(profileId: Long): String? =
        withContext(Dispatchers.IO) {
            val blob = prefs.getString(keyFor(profileId), null) ?: return@withContext null
            runCatching { cipher.decrypt(blob) }.getOrNull()
        }

    override suspend fun setPassword(profileId: Long, password: String?) =
        withContext(Dispatchers.IO) {
            val edit =
                prefs.edit().apply {
                    if (password == null) {
                        remove(keyFor(profileId))
                    } else {
                        putString(keyFor(profileId), cipher.encrypt(password))
                    }
                }
            check(edit.commit()) { "Failed to persist OBS credentials" }
        }

    private fun keyFor(profileId: Long) = "password_$profileId"

    private fun getOrCreateKey(): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "scenedeck_obs_passwords"
        const val PREFS_FILE = "scenedeck_secrets"
    }
}
