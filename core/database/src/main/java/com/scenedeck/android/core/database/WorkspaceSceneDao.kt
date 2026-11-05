package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Every read and deletion names its connection/collection; no global cleanup is exposed. */
@Dao
@Suppress("TooManyFunctions") // SQL for atomic scene identity and its local named-deck references.
interface WorkspaceSceneDao {

    @Query(
        "SELECT * FROM scene_registry WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName ORDER BY sortOrder ASC, sceneName ASC"
    )
    fun observeAll(profileId: Long, collectionName: String): Flow<List<SceneRegistryEntity>>

    @Query(
        "SELECT * FROM scene_registry WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName AND sceneName = :sceneName"
    )
    suspend fun byName(
        profileId: Long,
        collectionName: String,
        sceneName: String,
    ): SceneRegistryEntity?

    @Upsert suspend fun upsert(entity: SceneRegistryEntity)

    @Upsert suspend fun upsertAll(entities: List<SceneRegistryEntity>)

    @Query(
        "DELETE FROM scene_registry WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName AND sceneName = :sceneName"
    )
    suspend fun delete(profileId: Long, collectionName: String, sceneName: String)

    @Query(
        "DELETE FROM scene_registry WHERE connectionProfileId = :profileId " +
            "AND sceneCollectionName = :collectionName AND sceneName NOT IN (:validSceneNames)"
    )
    suspend fun deleteStale(profileId: Long, collectionName: String, validSceneNames: List<String>)

    @Query(
        "SELECT * FROM named_deck_members WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName AND deckId IN (SELECT deckId FROM named_deck_members " +
            "WHERE connectionProfileId = :profileId AND sceneCollectionName = :collectionName " +
            "AND sceneName = :oldName) " +
            "ORDER BY deckId, sortOrder, sceneName"
    )
    suspend fun membersAffectedByRename(
        profileId: Long,
        collectionName: String,
        oldName: String,
    ): List<NamedDeckMemberEntity>

    @Query(
        "DELETE FROM named_deck_members WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName AND deckId IN (:deckIds)"
    )
    suspend fun clearMembersForRename(
        profileId: Long,
        collectionName: String,
        deckIds: List<String>,
    )

    @Insert suspend fun insertRenamedMembers(members: List<NamedDeckMemberEntity>)

    /** Matches the registry conflict rule: an existing destination wins and keeps both names. */
    @Transaction
    suspend fun rename(
        profileId: Long,
        collectionName: String,
        oldName: String,
        newName: String,
    ): Boolean {
        if (oldName == newName) return false
        renameDeckReferences(profileId, collectionName, oldName, newName)
        val old = byName(profileId, collectionName, oldName)
        return if (old == null || byName(profileId, collectionName, newName) != null) false
        else {
            upsert(old.copy(sceneName = newName))
            delete(profileId, collectionName, oldName)
            true
        }
    }
}
