package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.designsystem.components.SceneMonitor
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

@Composable
internal fun DeckMonitors(expanded: Boolean, state: DeckState, previews: ScenePreviews) {
    if (!expanded) return
    Column(
        verticalArrangement = Arrangement.spacedBy(SceneDeckTheme.spacing.itemGap),
        modifier = Modifier.padding(vertical = SceneDeckTheme.spacing.sectionGap),
    ) {
        DeckMonitor("PROGRAM", state.currentProgramScene, state, previews, false)
        if (state.studioMode) DeckMonitor("PREVIEW", state.previewScene, state, previews, true)
    }
}

@Composable
private fun DeckMonitor(
    label: String,
    name: String?,
    state: DeckState,
    previews: ScenePreviews,
    preview: Boolean,
) {
    val scene = state.allScenes.firstOrNull { it.name == name }
    SceneMonitor(
        label = label,
        name = scene?.displayName ?: name,
        rawName = name,
        preview = preview,
        image = name?.let { previews.image(it)?.asImageBitmap() },
        imageStatus = name?.let(previews::label),
    )
}

@Composable
internal fun DeckAvailability(
    state: DeckState,
    options: NamedDeckSnapshot?,
    failed: Boolean,
    missing: Int,
    onRetry: () -> Unit,
    onManage: () -> Unit,
) {
    DeckWorkspaceStatus(state, onRetry)
    if (state.workspace != null && options == null)
        WorkspaceStatus(failed = failed, onRetry = onRetry)
    if (missing > 0)
        TextButton(onClick = onManage) { Text("$missing scenes unavailable · Manage deck") }
}
