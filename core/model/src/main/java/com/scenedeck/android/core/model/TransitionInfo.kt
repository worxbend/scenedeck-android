package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** A scene transition available in OBS (`GetSceneTransitionList`). */
@Immutable
data class TransitionInfo(
    val name: String,
    val kind: String,
    /** Fixed (non-configurable) transition; null duration when unknown. */
    val fixed: Boolean,
    val durationMs: Int?,
)

/** The currently selected scene transition (`GetCurrentSceneTransition`). */
@Immutable
data class CurrentTransition(
    val name: String,
    val kind: String,
    val durationMs: Int?,
    val configurable: Boolean,
    val fixed: Boolean,
)

/** `GetSceneTransitionList` result: current selection + all available. */
@Immutable
data class TransitionListSnapshot(
    val currentName: String,
    val transitions: List<TransitionInfo>,
)
