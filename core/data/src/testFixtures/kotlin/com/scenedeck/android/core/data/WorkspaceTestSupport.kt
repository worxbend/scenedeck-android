package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.database.WorkspaceSceneDao
import com.scenedeck.android.core.model.ObsSessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

val TEST_WORKSPACE = WorkspaceSession(WorkspaceKey(7, "Studio"), 1, ObsSessionToken())

class MutableWorkspaceContext(initial: WorkspaceSession = TEST_WORKSPACE) : WorkspaceContext {
    private val current = MutableStateFlow<WorkspaceState>(WorkspaceState.Ready(initial))
    override val state = current.asStateFlow()

    override fun isCurrent(session: WorkspaceSession) =
        current.value == WorkspaceState.Ready(session)

    override fun retry() = Unit

    fun select(session: WorkspaceSession) {
        current.value = WorkspaceState.Ready(session)
    }

    fun unavailable() {
        current.value = WorkspaceState.Unavailable
    }
}

/** Single-workspace feature fixture. Multi-workspace repository tests use the real Room DAO. */
fun singleWorkspaceRegistry(dao: SceneRegistryDao): RegistryRepository =
    RegistryRepository(SingleWorkspaceTestDao(dao), MutableWorkspaceContext(), dao)

private class SingleWorkspaceTestDao(private val delegate: SceneRegistryDao) : WorkspaceSceneDao {
    private fun checkScope(profileId: Long, collectionName: String) {
        check(WorkspaceKey(profileId, collectionName) == TEST_WORKSPACE.key)
    }

    override fun observeAll(profileId: Long, collectionName: String) =
        delegate.observeAll().also { checkScope(profileId, collectionName) }

    override suspend fun byName(
        profileId: Long,
        collectionName: String,
        sceneName: String,
    ): SceneRegistryEntity? {
        checkScope(profileId, collectionName)
        return delegate.byName(sceneName)
    }

    override suspend fun upsert(entity: SceneRegistryEntity) {
        checkScope(entity.connectionProfileId, entity.sceneCollectionName)
        delegate.upsert(entity.copy(connectionProfileId = 0, sceneCollectionName = ""))
    }

    override suspend fun upsertAll(entities: List<SceneRegistryEntity>) {
        entities.forEach { checkScope(it.connectionProfileId, it.sceneCollectionName) }
        delegate.upsertAll(
            entities.map { it.copy(connectionProfileId = 0, sceneCollectionName = "") }
        )
    }

    override suspend fun delete(profileId: Long, collectionName: String, sceneName: String) {
        checkScope(profileId, collectionName)
        delegate.delete(sceneName)
    }

    override suspend fun deleteStale(
        profileId: Long,
        collectionName: String,
        validSceneNames: List<String>,
    ) {
        checkScope(profileId, collectionName)
        delegate.deleteStale(validSceneNames)
    }

    override suspend fun rename(
        profileId: Long,
        collectionName: String,
        oldName: String,
        newName: String,
    ): Boolean {
        checkScope(profileId, collectionName)
        return delegate.rename(oldName, newName)
    }
}
