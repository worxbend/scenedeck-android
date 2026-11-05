package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.NamedDeckWorkspaceRow
import java.util.Locale

/** Built-ins keep their existing semantics; named decks have independent local membership. */
sealed interface DeckSelection {
    data object All : DeckSelection

    data object Primary : DeckSelection

    data class Named(val id: String) : DeckSelection
}

internal const val ALL_DECK_ID = "builtin:all"
internal const val PRIMARY_DECK_ID = "builtin:primary"

internal val DeckSelection.storageId: String
    get() =
        when (this) {
            DeckSelection.All -> ALL_DECK_ID
            DeckSelection.Primary -> PRIMARY_DECK_ID
            is DeckSelection.Named -> id
        }

data class NamedDeck(val id: String, val name: String, val sceneNames: List<String>)

data class NamedDeckSnapshot(
    val decks: List<NamedDeck> = emptyList(),
    val selection: DeckSelection = DeckSelection.All,
)

sealed interface NamedDeckState {
    data class Unavailable(val workspace: WorkspaceState) : NamedDeckState

    data class Loading(val origin: WorkspaceSession) : NamedDeckState

    data class Ready(val origin: WorkspaceSession, val snapshot: NamedDeckSnapshot) : NamedDeckState

    data class Failed(val origin: WorkspaceSession) : NamedDeckState
}

enum class DeckEditFailure {
    INVALID_NAME,
    DUPLICATE_NAME,
    TOO_MANY_DECKS,
    INVALID_MEMBERS,
    DECK_MISSING,
    DECK_CHANGED,
    INVALID_FILE,
}

/** Safe domain error; database/transport details never become UI copy. */
class DeckEditException(val reason: DeckEditFailure) : IllegalArgumentException(reason.name)

internal const val MAX_NAMED_DECKS = 100
internal const val MAX_DECK_NAME_LENGTH = 80
internal const val MAX_DECK_MEMBERS = 2_000
internal const val MAX_SCENE_REFERENCE_LENGTH = 4_096

internal fun checkedDeckName(raw: String): String =
    raw.trim().also { name ->
        if (name.isEmpty() || name.length > MAX_DECK_NAME_LENGTH || name.any(Char::isISOControl))
            throw DeckEditException(DeckEditFailure.INVALID_NAME)
    }

internal fun String.deckNameKey(): String = lowercase(Locale.ROOT)

internal fun checkedDeckMembers(names: List<String>): List<String> {
    if (
        names.size > MAX_DECK_MEMBERS ||
            names.any { it.isEmpty() || it.length > MAX_SCENE_REFERENCE_LENGTH }
    )
        throw DeckEditException(DeckEditFailure.INVALID_MEMBERS)
    return names.distinct()
}

internal fun List<NamedDeckWorkspaceRow>.toDeckSnapshot(): NamedDeckSnapshot {
    val decks = filter {
        it.deckId != null
    }
        .groupBy { requireNotNull(it.deckId) }
        .map { (id, rows) ->
            NamedDeck(id, requireNotNull(rows.first().deckName), rows.mapNotNull { it.sceneName })
        }
    val selected = firstOrNull()?.selectedDeckId ?: ALL_DECK_ID
    val selection =
        when {
            selected == PRIMARY_DECK_ID -> DeckSelection.Primary
            decks.any { it.id == selected } -> DeckSelection.Named(selected)
            else -> DeckSelection.All
        }
    return NamedDeckSnapshot(decks, selection)
}
