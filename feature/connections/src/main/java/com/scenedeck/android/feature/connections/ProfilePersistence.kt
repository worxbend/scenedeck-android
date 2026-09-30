package com.scenedeck.android.feature.connections

import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SecretsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Coordinates profile metadata and credentials without leaving an unusable new profile. */
@Suppress(
    "TooGenericExceptionCaught"
) // Recoverable storage failures require rollback before propagating.
internal class ProfilePersistence(
    private val profiles: ProfileRepository,
    private val secrets: SecretsStore,
) {
    suspend fun save(id: Long?, draft: ProfileDraft): Long =
        if (id == null) create(draft) else update(id, draft)

    suspend fun passwordForTest(entered: String?, profileId: Long?): String? =
        entered?.takeIf { it.isNotBlank() } ?: profileId?.let { secrets.passwordFor(it) }

    suspend fun delete(id: Long) {
        val previousPassword = secrets.passwordFor(id)
        try {
            secrets.setPassword(id, null)
            profiles.delete(id)
        } catch (failure: Exception) {
            rollback(failure) { secrets.setPassword(id, previousPassword) }
            throw failure
        }
    }

    private suspend fun create(draft: ProfileDraft): Long {
        val id = profiles.add(draft.name, draft.host, draft.port)
        try {
            writePassword(id, draft.password)
            return id
        } catch (failure: Exception) {
            rollback(failure) { profiles.delete(id) }
            rollback(failure) { secrets.setPassword(id, null) }
            throw failure
        }
    }

    private suspend fun update(id: Long, draft: ProfileDraft): Long {
        val existing = requireNotNull(profiles.byId(id)) { "Profile no longer exists" }
        val replacesPassword = !draft.password.isNullOrBlank()
        val previousPassword = if (replacesPassword) secrets.passwordFor(id) else null
        try {
            writePassword(id, draft.password)
            profiles.update(
                existing.copy(name = draft.name.trim(), host = draft.host.trim(), port = draft.port)
            )
        } catch (failure: Exception) {
            if (replacesPassword) rollback(failure) { secrets.setPassword(id, previousPassword) }
            throw failure
        }
        return id
    }

    private suspend fun writePassword(id: Long, password: String?) {
        if (!password.isNullOrBlank()) secrets.setPassword(id, password)
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Best-effort rollback must preserve the original failure/cancellation.
    private suspend fun rollback(failure: Exception, action: suspend () -> Unit) {
        withContext(NonCancellable) {
            try {
                action()
            } catch (cleanupFailure: Exception) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
        }
    }
}
