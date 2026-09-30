package com.scenedeck.android.core.data

/**
 * Keystore-backed storage for OBS passwords. Passwords NEVER touch Room, DataStore or logs
 * (docs/ARCHITECTURE.md rule 5).
 */
interface SecretsStore {
    /**
     * Returns the stored password, or null when none was saved.
     *
     * @throws SecretsDecryptException when a blob exists but can never be decrypted again (e.g. the
     *   Keystore key was invalidated); the undecryptable blob is deleted before the exception is
     *   thrown, so callers should treat this as "prompt the user to re-enter".
     */
    suspend fun passwordFor(profileId: Long): String?

    /** `null` removes the stored password. */
    suspend fun setPassword(profileId: Long, password: String?)
}

/**
 * A stored password blob could not be decrypted — typically the AndroidKeyStore key was invalidated
 * (lock-screen change) or the blob was tampered with. Never carries credential material; safe to
 * surface as a sanitized "re-enter your password" failure.
 */
class SecretsDecryptException(cause: Exception) :
    Exception("Stored OBS password is no longer readable", cause)
