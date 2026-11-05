package com.scenedeck.android.feature.live

import com.scenedeck.android.core.data.DeckSelection
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.WorkspaceSession

internal data class NamedDeckProjection(
    val scenes: List<SceneCardState>,
    val positions: Map<String, Int>,
    val missing: List<String> = emptyList(),
) {
    fun allowsReorder(selection: DeckSelection, query: String): Boolean =
        selection != DeckSelection.Primary && query.isBlank() && missing.isEmpty()
}

/** Live-only projection: canonical curation, diagnostics and widget selection stay independent. */
internal fun projectNamedDeck(state: DeckState, snapshot: NamedDeckSnapshot?): NamedDeckProjection {
    if (snapshot == null) return NamedDeckProjection(emptyList(), emptyMap())
    val selection = snapshot.selection
    val named =
        (selection as? DeckSelection.Named)?.let { selected ->
            snapshot.decks.find { it.id == selected.id }
        }
    return if (named != null) {
        val scenes = state.allScenes.associateBy { it.name }
        NamedDeckProjection(
            named.sceneNames.mapNotNull(scenes::get),
            named.sceneNames.withIndex().associate { it.value to it.index + 1 },
            named.sceneNames.filter { it !in scenes },
        )
    } else {
        val scenes = if (selection == DeckSelection.Primary) state.scenes else state.allScenes
        NamedDeckProjection(scenes, scenePositions(scenes))
    }
}

internal fun reorderSelectedDeck(
    live: LiveViewModel,
    decks: NamedDeckViewModel,
    origin: WorkspaceSession?,
    options: NamedDeckSnapshot?,
    names: List<String>,
) {
    if (origin == null) return
    when (val selection = options?.selection) {
        DeckSelection.All -> live.reorderDeck(origin, names)
        is DeckSelection.Named ->
            options.decks.find { it.id == selection.id }?.let { decks.reorder(origin, it, names) }
        else -> Unit
    }
}
