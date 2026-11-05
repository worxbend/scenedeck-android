package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Local bindings survive profile removal. Disabled bindings still reserve their chord. */
@Entity(
    tableName = "shortcut_bindings",
    primaryKeys = ["connectionProfileId", "sceneCollectionName", "bindingId"],
    indices =
        [
            Index(
                value = ["connectionProfileId", "sceneCollectionName", "key", "modifiers"],
                unique = true,
            )
        ],
)
data class ShortcutBindingEntity(
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val bindingId: String,
    val revision: Long,
    val key: String,
    val modifiers: Int,
    val enabled: Boolean,
    val payload: String,
)

@Dao
interface ShortcutBindingDao {
    @Query(
        "SELECT * FROM shortcut_bindings WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName ORDER BY `key`, modifiers, bindingId LIMIT :limit"
    )
    fun observe(
        profileId: Long,
        collectionName: String,
        limit: Int,
    ): Flow<List<ShortcutBindingEntity>>

    @Query(
        "SELECT * FROM shortcut_bindings WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName ORDER BY `key`, modifiers, bindingId LIMIT :limit"
    )
    suspend fun list(
        profileId: Long,
        collectionName: String,
        limit: Int,
    ): List<ShortcutBindingEntity>

    @Upsert suspend fun save(binding: ShortcutBindingEntity)

    @Query(
        "DELETE FROM shortcut_bindings WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName AND bindingId = :id"
    )
    suspend fun delete(profileId: Long, collectionName: String, id: String)
}
