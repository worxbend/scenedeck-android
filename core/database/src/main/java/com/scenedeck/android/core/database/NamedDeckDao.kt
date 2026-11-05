package com.scenedeck.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// The seed row ensures an empty workspace still publishes its built-in selection. All joins
// carry the complete namespace; a repeated deck ID in another workspace cannot leak members.
private const val WORKSPACE_DECK_QUERY =
    """
    SELECT COALESCE(s.selectedDeckId, 'builtin:all') AS selectedDeckId,
        d.deckId AS deckId, d.name AS deckName, m.sceneName AS sceneName,
        m.sortOrder AS sortOrder
    FROM (SELECT :profileId AS profileId, :collectionName AS collectionName) w
    LEFT JOIN workspace_deck_selection s
        ON s.connectionProfileId = w.profileId AND s.sceneCollectionName = w.collectionName
    LEFT JOIN named_decks d
        ON d.connectionProfileId = w.profileId AND d.sceneCollectionName = w.collectionName
    LEFT JOIN named_deck_members m
        ON m.connectionProfileId = d.connectionProfileId
        AND m.sceneCollectionName = d.sceneCollectionName AND m.deckId = d.deckId
    ORDER BY d.nameKey, d.deckId, m.sortOrder, m.sceneName
"""

/** Mutations are coordinated by one Room transaction in the data repository. */
@Dao
@Suppress(
    "TooManyFunctions"
) // Typed SQL for one scoped aggregate; transaction policy stays in data.
interface NamedDeckDao {
    @Query(WORKSPACE_DECK_QUERY)
    fun observeWorkspace(profileId: Long, collectionName: String): Flow<List<NamedDeckWorkspaceRow>>

    @Query(WORKSPACE_DECK_QUERY)
    suspend fun snapshot(profileId: Long, collectionName: String): List<NamedDeckWorkspaceRow>

    @Query(
        "SELECT * FROM named_decks WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName AND deckId = :deckId"
    )
    suspend fun byId(profileId: Long, collectionName: String, deckId: String): NamedDeckEntity?

    @Query(
        "SELECT * FROM named_decks WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName AND nameKey = :nameKey"
    )
    suspend fun byName(profileId: Long, collectionName: String, nameKey: String): NamedDeckEntity?

    @Insert suspend fun insert(deck: NamedDeckEntity)

    @Query(
        "UPDATE named_decks SET name = :name, nameKey = :nameKey WHERE " +
            "connectionProfileId = :profileId AND sceneCollectionName = :collectionName AND deckId = :deckId"
    )
    suspend fun rename(
        profileId: Long,
        collectionName: String,
        deckId: String,
        name: String,
        nameKey: String,
    ): Int

    @Query(
        "DELETE FROM named_decks WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName AND deckId = :deckId"
    )
    suspend fun delete(profileId: Long, collectionName: String, deckId: String): Int

    @Query(
        "DELETE FROM named_deck_members WHERE connectionProfileId = :profileId AND " +
            "sceneCollectionName = :collectionName AND deckId = :deckId"
    )
    suspend fun clearMembers(profileId: Long, collectionName: String, deckId: String)

    @Insert suspend fun insertMembers(members: List<NamedDeckMemberEntity>)

    @Upsert suspend fun select(selection: WorkspaceDeckSelectionEntity)

    @Query(
        "UPDATE workspace_deck_selection SET selectedDeckId = 'builtin:all' WHERE " +
            "connectionProfileId = :profileId AND sceneCollectionName = :collectionName AND selectedDeckId = :deckId"
    )
    suspend fun clearSelection(profileId: Long, collectionName: String, deckId: String)
}
