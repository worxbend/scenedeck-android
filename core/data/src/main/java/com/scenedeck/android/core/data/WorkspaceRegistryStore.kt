package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryEntity
import com.scenedeck.android.core.database.WorkspaceSceneDao
import kotlinx.coroutines.flow.first

/** Bound before a mutation waits for the mutex; every DAO call retains that same namespace. */
internal class WorkspaceRegistryStore(
    private val dao: WorkspaceSceneDao,
    private val origin: WorkspaceSession,
    private val workspaces: WorkspaceContext,
) {
    private val key = origin.key

    suspend fun all(): List<SceneRegistryEntity> = checked {
        dao.observeAll(key.profileId, key.collectionName).first()
    }

    suspend fun byName(name: String): SceneRegistryEntity? = checked {
        dao.byName(key.profileId, key.collectionName, name)
    }

    suspend fun upsert(entity: SceneRegistryEntity) = checked { dao.upsert(entity.inWorkspace()) }

    suspend fun upsertAll(entities: List<SceneRegistryEntity>) = checked {
        dao.upsertAll(entities.map { it.inWorkspace() })
    }

    suspend fun delete(name: String) = checked {
        dao.delete(key.profileId, key.collectionName, name)
    }

    suspend fun deleteStale(names: List<String>) = checked {
        dao.deleteStale(key.profileId, key.collectionName, names)
    }

    suspend fun rename(old: String, new: String): Boolean = checked {
        dao.rename(key.profileId, key.collectionName, old, new)
    }

    private suspend fun <T> checked(action: suspend () -> T): T {
        workspaces.requireCurrent(origin)
        val result = action()
        workspaces.requireCurrent(origin)
        return result
    }

    private fun SceneRegistryEntity.inWorkspace() =
        copy(connectionProfileId = key.profileId, sceneCollectionName = key.collectionName)
}
