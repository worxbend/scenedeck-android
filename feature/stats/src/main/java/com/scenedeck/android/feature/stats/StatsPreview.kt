package com.scenedeck.android.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.designsystem.components.TrendSamplesHolder
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo

private val previewState =
    StatsUiState(
        connection = ConnectionState.Ready(ObsVersionInfo("31.0.1", "5.6.1", 1, "test")),
        sampleCount = 12,
        fps = 59.9f,
        renderTimeMs = 1.4f,
        droppedPct = 0.2f,
        congestionPct = 12f,
        cpuUsagePct = 11.5,
        memoryUsageMb = 512.0,
        bitrateKbps = 6000,
        renderTotalFrames = 42_000,
        renderSkippedFrames = 3,
        outputTotalFrames = 41_800,
        outputSkippedFrames = 12,
        streamBytes = 52_000_000,
        streamActive = true,
        recordActive = true,
        targetFps = 60f,
    )

@Composable
private fun StatsPreviewContent(uiState: StatsUiState) {
    SceneDeckTheme {
        StatsContent(
            uiState = uiState,
            fpsTrend = TrendSamplesHolder(),
            renderTrend = TrendSamplesHolder(),
            frameDrops = FrameDropSeriesHolder(),
            motionLevel = MotionLevel.OFF,
        )
    }
}

/** Streaming + recording on a 60 fps canvas: red STREAM LIVE chip, amber REC chip. */
@PreviewLightDark
@Composable
private fun StatsScreenPreview() {
    StatsPreviewContent(previewState)
}

/** OBS default 30 fps canvas: a healthy 30 fps reading stays "running smoothly". */
@PreviewLightDark
@Composable
private fun StatsScreenThirtyFpsPreview() {
    StatsPreviewContent(
        previewState.copy(fps = 30f, renderTimeMs = 0.6f, droppedPct = 0f, targetFps = 30f)
    )
}
