package com.scenedeck.android.core.data

import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AesGcmCipherTest {

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encryptDecryptRoundTrip() {
        val cipher = AesGcmCipher(newKey())
        val plaintext = "supersecretpassword"
        val blob = cipher.encrypt(plaintext)

        assertNotEquals(plaintext, blob)
        assertEquals(plaintext, cipher.decrypt(blob))
    }

    @Test
    fun decryptWithWrongKeyFails() {
        val blob = AesGcmCipher(newKey()).encrypt("secret")
        assertFailsWithAny { AesGcmCipher(newKey()).decrypt(blob) }
    }

    @Test
    fun tamperedBlobFails() {
        val cipher = AesGcmCipher(newKey())
        val blob = cipher.encrypt("secret")
        val tampered = blob.substring(0, blob.length - 4) + "AAAA"
        assertFailsWithAny { cipher.decrypt(tampered) }
    }

    private inline fun assertFailsWithAny(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        throw AssertionError("Expected an exception but nothing was thrown")
    }
}
