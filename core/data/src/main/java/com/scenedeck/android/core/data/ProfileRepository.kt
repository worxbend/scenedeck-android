package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.ConnectionProfileEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed store of named OBS connection profiles (FEATURE_SPEC §1). */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ConnectionProfileDao,
) {
    /** All profiles, most-recently-used first. */
    val profiles: Flow<List<ConnectionProfile>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun byId(id: Long): ConnectionProfile? = dao.byId(id)?.toDomain()

    suspend fun lastUsed(): ConnectionProfile? = dao.lastUsed()?.toDomain()

    suspend fun add(name: String, host: String, port: Int): Long = dao.insert(
        ConnectionProfileEntity(
            name = name.trim(),
            host = host.trim(),
            port = port,
            createdAt = System.currentTimeMillis(),
        ),
    )

    suspend fun update(profile: ConnectionProfile) = dao.update(
        ConnectionProfileEntity(
            id = profile.id,
            name = profile.name.trim(),
            host = profile.host.trim(),
            port = profile.port,
            createdAt = profile.createdAt,
            lastUsedAt = profile.lastUsedAt,
        ),
    )

    suspend fun markUsed(id: Long) = dao.markUsed(id, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.deleteById(id)
}
