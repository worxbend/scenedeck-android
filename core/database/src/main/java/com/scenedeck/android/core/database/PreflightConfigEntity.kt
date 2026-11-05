package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One versioned local configuration; no credentials or OBS paths. */
@Entity(
    tableName = "preflight_config",
    primaryKeys = ["connectionProfileId", "sceneCollectionName"],
)
data class PreflightConfigEntity(
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val payload: String,
)

@Dao
interface PreflightConfigDao {
    @Query(
        "SELECT * FROM preflight_config WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName"
    )
    fun observe(profileId: Long, collectionName: String): Flow<PreflightConfigEntity?>

    @Query(
        "SELECT * FROM preflight_config WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName"
    )
    suspend fun get(profileId: Long, collectionName: String): PreflightConfigEntity?

    @Upsert suspend fun save(config: PreflightConfigEntity)
}
