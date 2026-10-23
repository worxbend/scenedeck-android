package com.scenedeck.android.feature.onboarding

import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.ConnectionProfileEntity
import kotlinx.coroutines.flow.flowOf

internal class MemorySecrets : SecretsStore {
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

internal class MemoryProfiles : ConnectionProfileDao {
    val rows = mutableMapOf<Long, ConnectionProfileEntity>()
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
        rows[entity.id] = entity
    }

    override suspend fun markUsed(id: Long, usedAt: Long) = Unit

    override suspend fun delete(entity: ConnectionProfileEntity) = deleteById(entity.id)

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }
}
