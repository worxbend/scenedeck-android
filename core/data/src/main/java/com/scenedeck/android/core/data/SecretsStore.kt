package com.scenedeck.android.core.data

/**
 * Keystore-backed storage for OBS passwords. Passwords NEVER touch Room, DataStore or logs
 * (docs/ARCHITECTURE.md rule 5).
 */
interface SecretsStore {
    suspend fun passwordFor(profileId: Long): String?

    /** `null` removes the stored password. */
    suspend fun setPassword(profileId: Long, password: String?)
}
