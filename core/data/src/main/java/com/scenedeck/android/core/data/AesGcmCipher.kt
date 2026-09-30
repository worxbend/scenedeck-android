package com.scenedeck.android.core.data

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM/NoPadding blob cipher. Kept AndroidKeyStore-free on purpose so the crypto round-trip is
 * unit-testable on the JVM with any [SecretKey]; production keys come from [KeystoreSecretsStore].
 */
internal class AesGcmCipher(private val key: SecretKey) {

    /** Encrypts to base64(IV ‖ ciphertext+tag). */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /** Decrypts base64(IV ‖ ciphertext+tag); throws on tampering or wrong key. */
    fun decrypt(blob: String): String {
        val bytes = Base64.getDecoder().decode(blob)
        require(bytes.size > GCM_IV_LENGTH) { "ciphertext blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes, 0, GCM_IV_LENGTH),
        )
        return String(
            cipher.doFinal(bytes, GCM_IV_LENGTH, bytes.size - GCM_IV_LENGTH),
            Charsets.UTF_8,
        )
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
