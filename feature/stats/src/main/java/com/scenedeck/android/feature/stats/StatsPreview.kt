package com.scenedeck.android.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.designsystem.components.TrendSamplesHolder
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo

/**
 * Stats page — live stream telemetry dashboard (FEATURE_SPEC §5, milestone M5): threshold gauges,
 * 2-minute trend charts, per-sample dropped-frame bars and counter cards, refreshed at 1 Hz from
 * the `StatsRepository` poll loop.
 */
@PreviewLightDark
@Composable
private fun StatsScreenPreview() {
    SceneDeckTheme {
        StatsContent(
            uiState =
                StatsUiState(
                    connection =
                        ConnectionState.Ready(ObsVersionInfo("31.0.1", "5.6.1", 1, "test")),
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
                ),
            fpsTrend = TrendSamplesHolder(),
            renderTrend = TrendSamplesHolder(),
            frameDrops = FrameDropSeriesHolder(),
            motionLevel = MotionLevel.OFF,
        )
    }
}
