package com.scenedeck.android.feature.live

import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.PreviewFreshness
import com.scenedeck.android.core.data.ScenePreviewStatus
import com.scenedeck.android.core.data.SceneThumbnail
import com.scenedeck.android.core.data.scenePreviewStatus
import com.scenedeck.android.core.model.ConnectionState

internal fun previewStatusText(status: ScenePreviewStatus): String {
    val seconds = status.ageSeconds ?: 0
    val age = if (seconds < 60) "${seconds}s" else "${seconds / 60}m"
    return when (status.freshness) {
        PreviewFreshness.FRESH -> "Preview · $age ago"
        PreviewFreshness.STALE -> "Stale preview · $age ago"
        PreviewFreshness.PENDING -> "Preview pending"
        PreviewFreshness.UNAVAILABLE -> "Preview unavailable"
        PreviewFreshness.DISCONNECTED -> "Previews disconnected"
    }
}

internal data class ScenePreviews(
    val frames: Map<String, SceneThumbnail>,
    val failed: Set<String>,
    val nowMs: Long,
    val enabled: Boolean,
    val connected: Boolean,
) {
    fun image(name: String) = frames[name]?.bitmap.takeIf { enabled && connected }

    fun label(name: String): String? =
        if (!enabled) null
        else
            previewStatusText(
                scenePreviewStatus(frames[name]?.capturedAtMs, nowMs, name in failed, connected)
            )
}

internal val DeckState.previewConnected: Boolean
    get() = workspace != null && connectionState is ConnectionState.Ready
