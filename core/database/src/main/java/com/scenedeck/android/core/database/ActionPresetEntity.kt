package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Local definitions only. Profile removal preserves them, like named decks and preflight. */
@Entity(
    tableName = "action_presets",
    primaryKeys = ["connectionProfileId", "sceneCollectionName", "presetId"],
    indices =
        [Index(value = ["connectionProfileId", "sceneCollectionName", "nameKey"], unique = true)],
)
data class ActionPresetEntity(
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val presetId: String,
    val name: String,
    val nameKey: String,
    val revision: Long,
    val payload: String,
)

@Dao
interface ActionPresetDao {
    @Query(
        "SELECT * FROM action_presets WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName ORDER BY nameKey, presetId LIMIT :limit"
    )
    fun observe(profileId: Long, collectionName: String, limit: Int): Flow<List<ActionPresetEntity>>

    @Query(
        "SELECT * FROM action_presets WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName ORDER BY nameKey, presetId LIMIT :limit"
    )
    suspend fun list(profileId: Long, collectionName: String, limit: Int): List<ActionPresetEntity>

    @Upsert suspend fun save(preset: ActionPresetEntity)

    @Query(
        "DELETE FROM action_presets WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName AND presetId = :id"
    )
    suspend fun delete(profileId: Long, collectionName: String, id: String)
}
