package com.scenedeck.android.feature.onboarding

import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SecretsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The single profile an onboarding run connects through.
 *
 * The first Connect press creates it; every retry (wrong host, wrong password, …) rewrites the same
 * row and credential instead of adding another "My OBS" profile. A blank password clears any
 * previously stored one, so a failed attempt never leaves a stale secret behind.
 *
 * @param draftId id persisted by an earlier attempt (restored from saved state), if any.
 * @param onDraftCreated called once the row exists so the caller can persist its id.
 */
internal class OnboardingDraftProfile(
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
    draftId: Long? = null,
    private val onDraftCreated: (Long) -> Unit = {},
) {
    var draftId: Long? = draftId
        private set

    /** Creates or updates the draft profile and returns its id. */
    suspend fun save(name: String, host: String, port: Int, password: String?): Long {
        val displayName = name.trim().ifBlank { DEFAULT_NAME }
        val existing = draftId?.let { profiles.byId(it) }
        val id =
            if (existing != null) {
                // Row and credential change together, so a cancelled retry never pairs the new
                // host with the previous attempt's password.
                withContext(NonCancellable) {
                    profiles.update(
                        existing.copy(name = displayName, host = host.trim(), port = port)
                    )
                    secrets.setPassword(existing.id, password?.takeIf { it.isNotBlank() })
                }
                return existing.id
            } else {
                create(displayName, host, port)
            }
        secrets.setPassword(id, password?.takeIf { it.isNotBlank() })
        return id
    }

    // The row and its remembered id must not be split by cancellation, or a retry would add a
    // duplicate profile.
    private suspend fun create(name: String, host: String, port: Int): Long =
        withContext(NonCancellable) {
            profiles.add(name, host, port).also { id ->
                draftId = id
                onDraftCreated(id)
            }
        }

    companion object {
        const val DEFAULT_NAME = "My OBS"
    }
}
