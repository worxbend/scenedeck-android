package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** A scene as reported by OBS. Curation metadata lives in the registry, never in OBS. */
@Immutable
data class SceneSummary(
    val name: String,
    val index: Int,
)

/** Result of `GetSceneList`: the full scene list plus the current program scene. */
@Immutable
data class SceneListSnapshot(
    val currentProgramScene: String?,
    val scenes: List<SceneSummary>,
)
