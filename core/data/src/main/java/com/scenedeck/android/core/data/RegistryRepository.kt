package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.WorkspaceSceneDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local scene registry (curation metadata) backed by Room (FEATURE_SPEC §6). */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RegistryRepository
@Inject
constructor(
    private val dao: WorkspaceSceneDao,
    val workspaces: WorkspaceContext,
    private val legacyDao: SceneRegistryDao,
) {
    /**
     * Serializes compound read-modify-write mutations so interleaved calls can't silently lose
     * updates (this singleton is the only writer of the registry table).
     */
    private val mutationMutex = Mutex()

    private val refresh = MutableStateFlow(0L)

    val state: Flow<RegistryState> =
        combine(workspaces.state, refresh) { workspace, _ -> workspace }
            .flatMapLatest { workspace ->
                flow<RegistryState> {
                        val origin = (workspace as? WorkspaceState.Ready)?.session
                        if (origin == null || !workspaces.isCurrent(origin)) {
                            emit(RegistryState.Unavailable(workspaces.state.value))
                        } else {
                            emit(RegistryState.Loading(origin))
                            dao.observeAll(origin.key.profileId, origin.key.collectionName)
                                .collect { rows ->
                                    workspaces.requireCurrent(origin)
                                    emit(RegistryState.Ready(origin, rows.map { it.toDomain() }))
                                }
                        }
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        val origin = (workspace as? WorkspaceState.Ready)?.session
                        emit(
                            if (origin != null && workspaces.isCurrent(origin))
                                RegistryState.Failed(origin)
                            else RegistryState.Unavailable(workspaces.state.value)
                        )
                    }
            }

    /** Old global curation is preserved for explicit recovery; never used as a scoped fallback. */
    val legacyEntries: Flow<List<SceneRegistryEntry>> =
        legacyDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun retry() {
        workspaces.retry()
        refresh.update { it + 1 }
    }

    suspend fun byName(origin: WorkspaceSession, sceneName: String): SceneRegistryEntry? =
        store(origin).byName(sceneName)?.toDomain()

    /**
     * Moves curation (role, colour, icon, position) from [oldName] to [newName] after a rename in
     * OBS, atomically. An existing entry for [newName] wins: nothing is migrated, both entries are
     * kept (the old one becomes stale for Doctor/Inventory cleanup), so curation made for the new
     * name is never overwritten. Returns true when the entry moved. Local registry only; OBS is
     * never mutated.
     */
    suspend fun rename(origin: WorkspaceSession, oldName: String, newName: String): Boolean =
        mutate(origin) { dao ->
            dao.rename(oldName, newName)
        }

    /** Removes registry entries whose scene no longer exists in OBS. */
    suspend fun deleteStale(origin: WorkspaceSession, validSceneNames: List<String>) =
        mutate(origin) { dao ->
            if (validSceneNames.isEmpty()) return@mutate // never nuke the registry on a failed read
            dao.deleteStale(validSceneNames)
        }

    /** Removes one entry (e.g. Doctor "remove stale" fix). */
    /** Removes [sceneName]'s entry and returns it (for undo), read and deleted as one step. */
    suspend fun remove(origin: WorkspaceSession, sceneName: String): SceneRegistryEntry? =
        mutate(origin) { dao ->
            dao.byName(sceneName)?.toDomain()?.also { dao.delete(sceneName) }
        }

    /**
     * Restores an entry removed by [remove] (Inventory undo) with its role, accent, icon and saved
     * position. Curation saved for the same name since the removal wins and is kept. The saved
     * position is restored as-is: after a reorder or import in between it may tie with another
     * entry, which then sorts by name until the next reorder renumbers densely.
     */
    suspend fun restore(origin: WorkspaceSession, entry: SceneRegistryEntry) =
        mutate(origin) { dao ->
            if (dao.byName(entry.sceneName) == null) dao.upsert(entry.toEntity())
        }

    /** Current entries as a one-shot list (export), in saved order. */
    suspend fun snapshot(origin: WorkspaceSession): List<SceneRegistryEntry> =
        store(origin).all().map { it.toDomain() }

    /** Patch one field without reading the other fields outside the mutation lock. */
    suspend fun edit(
        origin: WorkspaceSession,
        sceneName: String,
        change: (SceneRegistryEntry) -> SceneRegistryEntry,
    ) =
        mutate(origin) { dao ->
            val existing =
                dao.byName(sceneName)?.toDomain()
                    ?: SceneRegistryEntry(sceneName, null, sortOrder = UNPLACED_SORT_ORDER)
            val updated = change(existing)
            require(updated.sceneName == sceneName) { "Curation edits cannot rename OBS scenes" }
            dao.upsert(updated.toEntity())
        }

    private fun store(origin: WorkspaceSession) = WorkspaceRegistryStore(dao, origin, workspaces)

    internal suspend fun <T> mutate(
        origin: WorkspaceSession,
        action: suspend (WorkspaceRegistryStore) -> T,
    ): T {
        val bound = store(origin)
        return mutationMutex.withLock {
            workspaces.requireCurrent(origin)
            action(bound)
        }
    }
}
