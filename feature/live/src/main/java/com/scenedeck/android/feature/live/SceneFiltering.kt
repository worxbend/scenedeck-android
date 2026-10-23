package com.scenedeck.android.feature.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector

internal fun filterScenes(scenes: List<SceneCardState>, query: String): List<SceneCardState> {
    val term = query.trim()
    return if (term.isEmpty()) scenes
    else scenes.filter { it.name.contains(term, ignoreCase = true) }
}

/** 1-based deck positions by scene name, taken from the unfiltered list (first duplicate wins). */
internal fun scenePositions(scenes: List<SceneCardState>): Map<String, Int> {
    val positions = LinkedHashMap<String, Int>(scenes.size)
    scenes.forEachIndexed { index, scene -> positions.putIfAbsent(scene.name, index + 1) }
    return positions
}

internal fun sceneIconFor(iconName: String?): ImageVector =
    iconName?.let { runCatching { SceneIcon.valueOf(it) }.getOrNull() }?.imageVector
        ?: SceneDeckIcons.Scenes

/**
 * Live page — the hero deck (FEATURE_SPEC §2, milestone M3). Adaptive grid of all scene cards (tap
 * = program, long-press = quick edit, grip = drag reorder), wired TransportBar, Output Safety
 * confirmations, designed offline states.
 */
