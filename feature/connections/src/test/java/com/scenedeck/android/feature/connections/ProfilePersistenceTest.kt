package com.scenedeck.android.feature.connections

import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.ConnectionProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePersistenceTest {
    private val dao = MemoryProfiles()
    private val secrets = MemorySecrets()
    private val persistence = ProfilePersistence(ProfileRepository(dao), secrets)
    private val draft = ProfileDraft("Studio", "host", 4455, "new-password")

    @Test
    fun failedSecretWriteRollsBackNewProfile(): Unit = runBlocking {
        secrets.failure = IllegalStateException("private internal error")
        expectFailure { persistence.save(null, draft) }
        assertTrue(dao.rows.isEmpty())
        assertTrue(secrets.passwords.isEmpty())
    }

    @Test
    fun cancelledSecretWriteRollsBackAndPropagatesCancellation(): Unit = runBlocking {
        val cancelled = CancellationException("cancelled")
        secrets.failure = cancelled
        assertEquals(cancelled, expectFailure { persistence.save(null, draft) })
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun failedMetadataUpdateRestoresPreviousCredentials(): Unit = runBlocking {
        val id = persistence.save(null, draft)
        dao.updateFailure = true
        expectFailure {
            persistence.save(id, draft.copy(password = "replacement", host = "other-host"))
        }
        assertEquals("new-password", secrets.passwords[id])
        assertEquals("host", dao.rows[id]?.host)
    }

    @Test
    fun failedDeleteRestoresCredentials(): Unit = runBlocking {
        val id = persistence.save(null, draft)
        dao.deleteFailure = true
        expectFailure { persistence.delete(id) }
        assertEquals("new-password", secrets.passwords[id])
        assertTrue(dao.rows.containsKey(id))
    }

    @Test
    fun editingWithoutPasswordRetainsSavedCredentialsForSaveAndTest(): Unit = runBlocking {
        val id = persistence.save(null, draft)
        persistence.save(id, draft.copy(password = null))
        assertEquals("new-password", persistence.passwordForTest("", id))
        assertEquals("entered", persistence.passwordForTest("entered", id))
        assertNull(persistence.passwordForTest(null, null))
        assertEquals("new-password", secrets.passwords[id])
    }

    private suspend fun expectFailure(action: suspend () -> Unit): Exception {
        try {
            action()
        } catch (failure: Exception) {
            return failure
        }
        throw AssertionError("Expected persistence failure")
    }

    private class MemorySecrets : SecretsStore {
        val passwords = mutableMapOf<Long, String>()
        var failure: Exception? = null

        override suspend fun passwordFor(profileId: Long) = passwords[profileId]

        override suspend fun setPassword(profileId: Long, password: String?) {
            failure?.let {
                failure = null
                throw it
            }
            if (password == null) passwords.remove(profileId) else passwords[profileId] = password
        }
    }

    private class MemoryProfiles : ConnectionProfileDao {
        val rows = mutableMapOf<Long, ConnectionProfileEntity>()
        var updateFailure = false
        var deleteFailure = false
        private var nextId = 1L

        override fun observeAll() = flowOf(rows.values.toList())

        override suspend fun byId(id: Long) = rows[id]

        override suspend fun lastUsed(): ConnectionProfileEntity? = null

        override suspend fun insert(entity: ConnectionProfileEntity): Long {
            val id = nextId++
            rows[id] = entity.copy(id = id)
            return id
        }

        override suspend fun update(entity: ConnectionProfileEntity) {
            check(!updateFailure)
            rows[entity.id] = entity
        }

        override suspend fun markUsed(id: Long, usedAt: Long) = Unit

        override suspend fun delete(entity: ConnectionProfileEntity) = deleteById(entity.id)

        override suspend fun deleteById(id: Long) {
            check(!deleteFailure)
            rows.remove(id)
        }
    }
}
