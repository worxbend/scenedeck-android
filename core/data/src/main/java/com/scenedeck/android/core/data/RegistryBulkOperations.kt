package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryEntity

/**
 * Persists a full order: [orderedSceneNames] become positions 0..n-1 and every other placed entry
 * (e.g. stale ones) follows in its previous relative order, so positions stay dense and unique.
 * Scenes without an entry get one with no role, so reordering never assigns roles.
 */
suspend fun RegistryRepository.reorder(origin: WorkspaceSession, orderedSceneNames: List<String>) =
    mutate(origin) { dao ->
        val all = dao.all()
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

/** Bulk-assigns a role to scenes without one (no entry or unassigned), keeping their order. */
suspend fun RegistryRepository.assignRoleToUnassigned(
    origin: WorkspaceSession,
    obsSceneNames: List<String>,
    role: SceneRole,
) =
    mutate(origin) { dao ->
        val byName = dao.all().associateBy { it.sceneName }
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
 * Merge-import: entries matched by sceneName are updated field-by-field and new names are inserted.
 * The file's order wins: its placed entries take the first positions in file order, local placed
 * entries the file doesn't mention follow in their previous order, and positions are renumbered
 * densely, so export -> import restores the same order. Returns (inserted, updated) counts for the
 * confirmation summary.
 */
suspend fun RegistryRepository.importMerge(
    origin: WorkspaceSession,
    imported: List<SceneRegistryEntry>,
): Pair<Int, Int> =
    mutate(origin) { dao ->
        var inserted = 0
        var updated = 0
        val local = dao.all()
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
                    localAlias = checkedSceneAlias(entry.localAlias) ?: existing?.localAlias,
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
        val positions = (filePlaced + localPlaced).withIndex().associate { it.value to it.index }
        val touched = filePosition.keys + localPlaced
        dao.upsertAll(
            touched.map { name ->
                val entity = byName.getValue(name)
                entity.copy(sortOrder = positions[name] ?: UNPLACED_SORT_ORDER)
            }
        )
        inserted to updated
    }
