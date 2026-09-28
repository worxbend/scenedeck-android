package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/**
 * A named OBS connection target. The password is NOT stored here — it lives in
 * Keystore-backed encrypted storage (:core:data SecretsStore, see docs/ARCHITECTURE.md).
 */
@Immutable
data class ObsProfile(
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT: Int = 4455
    }
}

/** Result of `GetProfileList`. */
@Immutable
data class ProfileListSnapshot(
    val currentProfile: String,
    val profiles: List<String>,
)

/** Result of `GetSceneCollectionList`. */
@Immutable
data class SceneCollectionListSnapshot(
    val currentCollection: String,
    val collections: List<String>,
)
