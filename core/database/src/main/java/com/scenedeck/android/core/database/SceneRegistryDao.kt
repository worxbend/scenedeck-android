package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneRegistryDao {

    @Query("SELECT * FROM scene_registry ORDER BY sortOrder ASC, sceneName ASC")
    fun observeAll(): Flow<List<SceneRegistryEntity>>

    @Query("SELECT * FROM scene_registry WHERE sceneName = :sceneName")
    suspend fun byName(sceneName: String): SceneRegistryEntity?

    @Upsert suspend fun upsert(entity: SceneRegistryEntity)

    @Upsert suspend fun upsertAll(entities: List<SceneRegistryEntity>)

    /** Deletes entries whose scene no longer exists in OBS. */
    @Query("DELETE FROM scene_registry WHERE sceneName NOT IN (:validSceneNames)")
    suspend fun deleteStale(validSceneNames: List<String>)

    @Query("DELETE FROM scene_registry WHERE sceneName = :sceneName")
    suspend fun delete(sceneName: String)

    /**
     * Moves the entry for [oldName] to [newName] in one transaction, so observers never see the
     * scene's curation half-moved and a crash can't leave both rows. Returns false (and changes
     * nothing) when [oldName] has no entry or [newName] already has one.
     */
    @Transaction
    suspend fun rename(oldName: String, newName: String): Boolean {
        val old = byName(oldName)
        if (old == null || oldName == newName || byName(newName) != null) return false
        upsert(old.copy(sceneName = newName))
        delete(oldName)
        return true
    }
}
