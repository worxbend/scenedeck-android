package com.scenedeck.android.feature.stats

import com.scenedeck.android.core.designsystem.components.GaugeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioHealthZoneTest {
    private val healthy = StatsUiState(fps = 60f, renderTimeMs = 1f)
    private val fallback = FrameRateThresholds.forTargetFps(null)

    @Test
    fun healthyReadingsAreNormal() {
        assertEquals(GaugeZone.NORMAL, studioHealthZone(healthy))
    }

    @Test
    fun fpsAtWarningBoundaryMatchesGaugeWarning() {
        assertEquals(
            GaugeZone.WARNING,
            studioHealthZone(healthy.copy(fps = fallback.fpsWarn)),
        )
    }

    @Test
    fun fpsAtCriticalBoundaryMatchesGaugeCritical() {
        assertEquals(
            GaugeZone.CRITICAL,
            studioHealthZone(healthy.copy(fps = fallback.fpsCrit)),
        )
    }

    @Test
    fun criticalReadingTakesPrecedenceOverWarnings() {
        val state =
            healthy.copy(
                droppedPct = StatsThresholds.DROPPED_WARN_PCT,
                renderTimeMs = fallback.renderCritMs,
            )
        assertEquals(GaugeZone.CRITICAL, studioHealthZone(state))
    }

    @Test
    fun healthyThirtyFpsCanvasIsNormal() {
        // E2E regression: OBS default 30 fps canvas, 0 % drops, 0.6 ms render.
        val state = StatsUiState(fps = 30f, renderTimeMs = 0.6f, targetFps = 30f)
        assertEquals(GaugeZone.NORMAL, studioHealthZone(state))
    }

    @Test
    fun thirtyFpsCanvasWarnsAndGoesCriticalRelativeToItsOwnTarget() {
        val base = StatsUiState(renderTimeMs = 0.6f, targetFps = 30f)
        assertEquals(GaugeZone.NORMAL, studioHealthZone(base.copy(fps = 28f)))
        assertEquals(GaugeZone.WARNING, studioHealthZone(base.copy(fps = 26f)))
        assertEquals(GaugeZone.CRITICAL, studioHealthZone(base.copy(fps = 22f)))
    }

    @Test
    fun ntscCanvasAtFullRateIsNormal() {
        val state = StatsUiState(fps = 59.94f, renderTimeMs = 2f, targetFps = 60_000f / 1_001f)
        assertEquals(GaugeZone.NORMAL, studioHealthZone(state))
    }

    @Test
    fun renderTimeUsesTheCanvasFrameBudget() {
        // 12 ms exceeds the 60 fps warn line (50 % of 16.7 ms = 8.3 ms) but stays below the
        // 30 fps warn line (50 % of 33.3 ms = 16.7 ms).
        val sixty = StatsUiState(fps = 60f, renderTimeMs = 12f, targetFps = 60f)
        val thirty = StatsUiState(fps = 30f, renderTimeMs = 12f, targetFps = 30f)
        assertEquals(GaugeZone.WARNING, studioHealthZone(sixty))
        assertEquals(GaugeZone.NORMAL, studioHealthZone(thirty))
    }
}
