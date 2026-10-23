package com.scenedeck.android.feature.onboarding

import com.scenedeck.android.core.data.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingDraftProfileTest {
    private val dao = MemoryProfiles()
    private val secrets = MemorySecrets()
    private val repository = ProfileRepository(dao)

    @Test
    fun retriesReuseOneProfileAndReplaceCredentials(): Unit = runBlocking {
        val draft = OnboardingDraftProfile(repository, secrets)

        val unreachable = draft.save("My OBS", "10.0.0.9", 4455, "secret")
        val wrongPassword = draft.save("My OBS", " 192.168.1.20 ", 4455, "wrong")
        val success = draft.save("Studio", "192.168.1.20", 4456, "right")

        assertEquals(unreachable, wrongPassword)
        assertEquals(unreachable, success)
        assertEquals(1, dao.rows.size)
        val row = dao.rows.getValue(success)
        assertEquals("Studio", row.name)
        assertEquals("192.168.1.20", row.host)
        assertEquals(4456, row.port)
        assertEquals(mapOf(success to "right"), secrets.passwords)
    }

    @Test
    fun blankPasswordOnRetryClearsStaleCredential(): Unit = runBlocking {
        val draft = OnboardingDraftProfile(repository, secrets)
        val id = draft.save("My OBS", "host", 4455, "wrong")

        draft.save("My OBS", "host", 4455, "  ")

        assertFalse(secrets.passwords.containsKey(id))
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun blankNameFallsBackToDefault(): Unit = runBlocking {
        val id = OnboardingDraftProfile(repository, secrets).save("  ", "host", 4455, null)

        assertEquals(OnboardingDraftProfile.DEFAULT_NAME, dao.rows.getValue(id).name)
        assertNull(secrets.passwords[id])
    }

    @Test
    fun restoredDraftIdIsReusedAfterRecreation(): Unit = runBlocking {
        var savedId: Long? = null
        val first = OnboardingDraftProfile(repository, secrets, onDraftCreated = { savedId = it })
        val id = first.save("My OBS", "host", 4455, "one")

        val recreated = OnboardingDraftProfile(repository, secrets, draftId = savedId)
        assertEquals(id, recreated.save("My OBS", "other", 4455, "two"))
        assertEquals(1, dao.rows.size)
        assertEquals("two", secrets.passwords[id])
    }

    @Test
    fun deletedDraftIsRecreatedOnce(): Unit = runBlocking {
        val draft = OnboardingDraftProfile(repository, secrets)
        val original = draft.save("My OBS", "host", 4455, null)
        dao.rows.remove(original)

        val replacement = draft.save("My OBS", "host", 4455, null)
        assertEquals(replacement, draft.save("My OBS", "host", 4455, null))
        assertEquals(setOf(replacement), dao.rows.keys)
    }

    @Test
    fun failedCredentialWriteStillReusesDraftOnRetry(): Unit = runBlocking {
        val draft = OnboardingDraftProfile(repository, secrets)
        secrets.failure = IllegalStateException("keystore unavailable")
        val failure = runCatching { draft.save("My OBS", "host", 4455, "secret") }
        assertEquals("keystore unavailable", failure.exceptionOrNull()?.message)

        val id = draft.save("My OBS", "host", 4455, "secret")
        assertEquals(1, dao.rows.size)
        assertEquals("secret", secrets.passwords[id])
    }
}
