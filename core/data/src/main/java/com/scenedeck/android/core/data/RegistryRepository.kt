package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local scene registry (curation metadata) backed by Room (FEATURE_SPEC §6). */
@Singleton
class RegistryRepository @Inject constructor(private val dao: SceneRegistryDao) {
    /**
     * Serializes compound read-modify-write mutations so interleaved calls can't silently lose
     * updates (this singleton is the only writer of the registry table).
     */
    private val mutationMutex = Mutex()

    val entries: Flow<List<SceneRegistryEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun byName(sceneName: String): SceneRegistryEntry? = dao.byName(sceneName)?.toDomain()

    /** Upserts curation metadata for a scene (quick-edit). */
    suspend fun update(
        sceneName: String,
        role: SceneRole,
        accentColorArgb: Long?,
        iconName: String?,
    ) = mutationMutex.withLock {
        updateLocked(sceneName, role, accentColorArgb, iconName)
    }

    private suspend fun updateLocked(
        sceneName: String,
        role: SceneRole,
        accentColorArgb: Long?,
        iconName: String?,
    ) {
        val existing = dao.byName(sceneName)
        dao.upsert(
            SceneRegistryEntity(
                sceneName = sceneName,
                role = role.name,
                accentColorArgb = accentColorArgb,
                iconName = iconName,
                sortOrder = existing?.sortOrder ?: Int.MAX_VALUE,
            )
        )
    }

    /** Persists a deck order: [orderedSceneNames] get sortOrder 0..n, others keep theirs. */
    suspend fun reorder(orderedSceneNames: List<String>) = mutationMutex.withLock {
        val existingByName = dao.observeAll().first().associateBy { it.sceneName }
        val entities = orderedSceneNames.mapIndexed { index, name ->
            val existing = existingByName[name]
            SceneRegistryEntity(
                sceneName = name,
                role = existing?.role ?: SceneRole.PRIMARY.name,
                accentColorArgb = existing?.accentColorArgb,
                iconName = existing?.iconName,
                sortOrder = index,
            )
        }
        dao.upsertAll(entities)
    }

    /** Removes registry entries whose scene no longer exists in OBS. */
    suspend fun deleteStale(validSceneNames: List<String>) = mutationMutex.withLock {
        if (validSceneNames.isEmpty()) return@withLock // never nuke the registry on a failed read
        dao.deleteStale(validSceneNames)
    }

    /** Removes one entry (e.g. Doctor "remove stale" fix). */
    suspend fun remove(sceneName: String) = mutationMutex.withLock { dao.delete(sceneName) }

    /** Current entries as a one-shot list (export). */
    suspend fun snapshot(): List<SceneRegistryEntry> = entries.first()

    /** Bulk-assigns a role to scenes that have NO entry yet (fast curation). */
    suspend fun assignRoleToUnassigned(obsSceneNames: List<String>, role: SceneRole) =
        mutationMutex.withLock {
            val existing = dao.observeAll().first().map { it.sceneName }.toSet()
            val entities =
                obsSceneNames
                    .distinct()
                    .filter { it !in existing }
                    .map { name ->
                        SceneRegistryEntity(
                            sceneName = name,
                            role = role.name,
                            sortOrder = Int.MAX_VALUE,
                        )
                    }
            dao.upsertAll(entities)
        }

    /**
     * Merge-import: entries matched by sceneName are updated field-by-field; new names are inserted
     * keeping their imported order when free. Returns (inserted, updated) counts for the
     * confirmation summary.
     */
    suspend fun importMerge(imported: List<SceneRegistryEntry>): Pair<Int, Int> =
        mutationMutex.withLock {
            var inserted = 0
            var updated = 0
            val byName = dao.observeAll().first().associateBy { it.sceneName }.toMutableMap()
            val changes = linkedMapOf<String, SceneRegistryEntity>()
            imported.forEach { entry ->
                val existing = byName[entry.sceneName]
                if (existing == null) inserted++ else updated++
                val entity =
                    SceneRegistryEntity(
                        sceneName = entry.sceneName,
                        role = entry.role.name,
                        accentColorArgb = entry.accentColorArgb ?: existing?.accentColorArgb,
                        iconName = entry.iconName ?: existing?.iconName,
                        sortOrder = existing?.sortOrder ?: entry.sortOrder,
                    )
                byName[entry.sceneName] = entity
                changes[entry.sceneName] = entity
            }
            dao.upsertAll(changes.values.toList())
            inserted to updated
        }
}
