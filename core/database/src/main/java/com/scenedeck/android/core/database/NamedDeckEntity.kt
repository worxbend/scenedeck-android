package com.scenedeck.android.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Local organization only; a deck never changes OBS scenes or diagnostic roles. */
@Entity(
    tableName = "named_decks",
    primaryKeys = ["connectionProfileId", "sceneCollectionName", "deckId"],
    indices =
        [Index(value = ["connectionProfileId", "sceneCollectionName", "nameKey"], unique = true)],
)
data class NamedDeckEntity(
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val deckId: String,
    val name: String,
    /** Trimmed, locale-independent lowercase name, validated by the data layer. */
    val nameKey: String,
)

@Entity(
    tableName = "named_deck_members",
    primaryKeys = ["connectionProfileId", "sceneCollectionName", "deckId", "sceneName"],
    foreignKeys =
        [
            ForeignKey(
                entity = NamedDeckEntity::class,
                parentColumns = ["connectionProfileId", "sceneCollectionName", "deckId"],
                childColumns = ["connectionProfileId", "sceneCollectionName", "deckId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class NamedDeckMemberEntity(
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val deckId: String,
    val sceneName: String,
    val sortOrder: Int,
)

/** Includes built-in selections; intentionally no profile FK or automatic curation deletion. */
@Entity(
    tableName = "workspace_deck_selection",
    primaryKeys = ["connectionProfileId", "sceneCollectionName"],
)
data class WorkspaceDeckSelectionEntity(
    val connectionProfileId: Long,
    val sceneCollectionName: String,
    val selectedDeckId: String,
)

/** One atomic query joins selection, decks and members, including the no-decks case. */
data class NamedDeckWorkspaceRow(
    val selectedDeckId: String,
    val deckId: String?,
    val deckName: String?,
    val sceneName: String?,
    val sortOrder: Int?,
)
