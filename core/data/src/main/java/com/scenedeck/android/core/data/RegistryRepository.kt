package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Local scene registry (curation metadata) backed by Room (FEATURE_SPEC §6). */
@Singleton
class RegistryRepository @Inject constructor(private val dao: SceneRegistryDao) {
    val entries: Flow<List<SceneRegistryEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun byName(sceneName: String): SceneRegistryEntry? = dao.byName(sceneName)?.toDomain()

    /** Upserts curation metadata for a scene (quick-edit). */
    suspend fun update(
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
    suspend fun reorder(orderedSceneNames: List<String>) {
        val entities = orderedSceneNames.mapIndexed { index, name ->
            val existing = dao.byName(name)
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
    suspend fun deleteStale(validSceneNames: List<String>) {
        if (validSceneNames.isEmpty()) return // never nuke the registry on a failed read
        dao.deleteStale(validSceneNames)
    }

    /** Removes one entry (e.g. Doctor "remove stale" fix). */
    suspend fun remove(sceneName: String) = dao.delete(sceneName)

    /** Current entries as a one-shot list (export). */
    suspend fun snapshot(): List<SceneRegistryEntry> = entries.first()

    /** Bulk-assigns a role to scenes that have NO entry yet (fast curation). */
    suspend fun assignRoleToUnassigned(obsSceneNames: List<String>, role: SceneRole) {
        val existing = dao.observeAll().first().map { it.sceneName }.toSet()
        obsSceneNames
            .filter { it !in existing }
            .forEach { name ->
                update(name, role, accentColorArgb = null, iconName = null)
            }
    }

    /**
     * Merge-import: entries matched by sceneName are updated field-by-field; new names are inserted
     * keeping their imported order when free. Returns (inserted, updated) counts for the
     * confirmation summary.
     */
    suspend fun importMerge(imported: List<SceneRegistryEntry>): Pair<Int, Int> {
        var inserted = 0
        var updated = 0
        imported.forEach { entry ->
            val existing = dao.byName(entry.sceneName)
            if (existing == null) inserted++ else updated++
            dao.upsert(
                SceneRegistryEntity(
                    sceneName = entry.sceneName,
                    role = entry.role.name,
                    accentColorArgb = entry.accentColorArgb ?: existing?.accentColorArgb,
                    iconName = entry.iconName ?: existing?.iconName,
                    sortOrder = existing?.sortOrder ?: entry.sortOrder,
                )
            )
        }
        return inserted to updated
    }
}
