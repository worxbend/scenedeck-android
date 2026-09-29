package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneRegistryDao {

    @Query("SELECT * FROM scene_registry ORDER BY sortOrder ASC, sceneName ASC")
    fun observeAll(): Flow<List<SceneRegistryEntity>>

    @Query("SELECT * FROM scene_registry WHERE sceneName = :sceneName")
    suspend fun byName(sceneName: String): SceneRegistryEntity?

    @Upsert
    suspend fun upsert(entity: SceneRegistryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SceneRegistryEntity>)

    /** Deletes entries whose scene no longer exists in OBS. */
    @Query("DELETE FROM scene_registry WHERE sceneName NOT IN (:validSceneNames)")
    suspend fun deleteStale(validSceneNames: List<String>)

    @Query("DELETE FROM scene_registry WHERE sceneName = :sceneName")
    suspend fun delete(sceneName: String)
}
