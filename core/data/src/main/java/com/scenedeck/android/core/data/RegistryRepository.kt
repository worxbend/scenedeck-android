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

    /** Upserts curation metadata for a scene (quick-edit); a null [role] stays unassigned. */
    suspend fun update(
        sceneName: String,
        role: SceneRole?,
        accentColorArgb: Long?,
        iconName: String?,
    ) = mutationMutex.withLock {
        val existing = dao.byName(sceneName)
        dao.upsert(
            SceneRegistryEntity(
                sceneName = sceneName,
                role = role?.name ?: UNASSIGNED_ROLE,
                accentColorArgb = accentColorArgb,
                iconName = iconName,
                sortOrder = existing?.sortOrder ?: UNPLACED_SORT_ORDER,
            )
        )
    }

    /**
     * Persists a full order: [orderedSceneNames] become positions 0..n-1 and every other placed
     * entry (e.g. stale ones) follows in its previous relative order, so positions stay dense and
     * unique. Scenes without an entry get one with no role, so reordering never assigns roles.
     */
    suspend fun reorder(orderedSceneNames: List<String>) = mutationMutex.withLock {
        val all = dao.observeAll().first()
        val byName = all.associateBy { it.sceneName }
        val ordered = orderedSceneNames.distinct()
        val orderedSet = ordered.toSet()
        val placedRest = all.filter {
            it.sceneName !in orderedSet && it.sortOrder != UNPLACED_SORT_ORDER
        }
        val sequence =
            ordered.map { name ->
                byName[name] ?: SceneRegistryEntity(sceneName = name, role = UNASSIGNED_ROLE)
            } + placedRest
        dao.upsertAll(sequence.mapIndexed { index, entity -> entity.copy(sortOrder = index) })
    }

    /**
     * Moves curation (role, colour, icon, position) from [oldName] to [newName] after a rename in
     * OBS, atomically. An existing entry for [newName] wins: nothing is migrated, both entries are
     * kept (the old one becomes stale for Doctor/Inventory cleanup), so curation made for the new
     * name is never overwritten. Returns true when the entry moved. Local registry only; OBS is
     * never mutated.
     */
    suspend fun rename(oldName: String, newName: String): Boolean = mutationMutex.withLock {
        dao.rename(oldName, newName)
    }

    /** Removes registry entries whose scene no longer exists in OBS. */
    suspend fun deleteStale(validSceneNames: List<String>) = mutationMutex.withLock {
        if (validSceneNames.isEmpty()) return@withLock // never nuke the registry on a failed read
        dao.deleteStale(validSceneNames)
    }

    /** Removes one entry (e.g. Doctor "remove stale" fix). */
    suspend fun remove(sceneName: String) = mutationMutex.withLock { dao.delete(sceneName) }

    /** Current entries as a one-shot list (export), in saved order. */
    suspend fun snapshot(): List<SceneRegistryEntry> = entries.first()

    /** Bulk-assigns a role to scenes without one (no entry or unassigned), keeping their order. */
    suspend fun assignRoleToUnassigned(obsSceneNames: List<String>, role: SceneRole) =
        mutationMutex.withLock {
            val byName = dao.observeAll().first().associateBy { it.sceneName }
            val entities =
                obsSceneNames.distinct().mapNotNull { name ->
                    val existing = byName[name]
                    when {
                        existing == null ->
                            SceneRegistryEntity(
                                sceneName = name,
                                role = role.name,
                                sortOrder = UNPLACED_SORT_ORDER,
                            )
                        existing.role == UNASSIGNED_ROLE -> existing.copy(role = role.name)
                        else -> null
                    }
                }
            dao.upsertAll(entities)
        }

    /**
     * Merge-import: entries matched by sceneName are updated field-by-field and new names are
     * inserted. The file's order wins: its placed entries take the first positions in file order,
     * local placed entries the file doesn't mention follow in their previous order, and positions
     * are renumbered densely, so export -> import restores the same order. Returns (inserted,
     * updated) counts for the confirmation summary.
     */
    suspend fun importMerge(imported: List<SceneRegistryEntry>): Pair<Int, Int> =
        mutationMutex.withLock {
            var inserted = 0
            var updated = 0
            val local = dao.observeAll().first()
            val byName = local.associateBy { it.sceneName }.toMutableMap()
            // First occurrence of a name fixes its position in the file.
            val filePosition = linkedMapOf<String, Pair<Int, Int>>()
            imported.forEachIndexed { index, entry ->
                val existing = byName[entry.sceneName]
                if (existing == null) inserted++ else updated++
                byName[entry.sceneName] =
                    SceneRegistryEntity(
                        sceneName = entry.sceneName,
                        role = entry.role?.name ?: existing?.role ?: UNASSIGNED_ROLE,
                        accentColorArgb = entry.accentColorArgb ?: existing?.accentColorArgb,
                        iconName = entry.iconName ?: existing?.iconName,
                        sortOrder = existing?.sortOrder ?: UNPLACED_SORT_ORDER,
                    )
                filePosition.getOrPut(entry.sceneName) { entry.sortOrder to index }
            }
            val filePlaced =
                filePosition.entries
                    .filter { it.value.first != UNPLACED_SORT_ORDER }
                    .sortedWith(compareBy({ it.value.first }, { it.value.second }))
                    .map { it.key }
            val filePlacedSet = filePlaced.toSet()
            val localPlaced =
                local
                    .filter { it.sortOrder != UNPLACED_SORT_ORDER }
                    .map { it.sceneName }
                    .filter { it !in filePlacedSet }
            val positions =
                (filePlaced + localPlaced).withIndex().associate { it.value to it.index }
            val touched = filePosition.keys + localPlaced
            dao.upsertAll(
                touched.map { name ->
                    val entity = byName.getValue(name)
                    entity.copy(sortOrder = positions[name] ?: UNPLACED_SORT_ORDER)
                }
            )
            inserted to updated
        }
}
