package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local scene registry (curation metadata) backed by Room (FEATURE_SPEC §6). */
@Singleton
class RegistryRepository @Inject constructor(
    private val dao: SceneRegistryDao,
) {
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
            ),
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
}
