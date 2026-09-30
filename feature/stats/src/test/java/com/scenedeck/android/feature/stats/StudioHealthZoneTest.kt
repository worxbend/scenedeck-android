package com.scenedeck.android.feature.stats

import com.scenedeck.android.core.designsystem.components.GaugeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioHealthZoneTest {
    private val healthy = StatsUiState(fps = 60f, renderTimeMs = 1f)

    @Test
    fun healthyReadingsAreNormal() {
        assertEquals(GaugeZone.NORMAL, studioHealthZone(healthy))
    }

    @Test
    fun fpsAtWarningBoundaryMatchesGaugeWarning() {
        assertEquals(
            GaugeZone.WARNING,
            studioHealthZone(healthy.copy(fps = StatsThresholds.FPS_WARN)),
        )
    }

    @Test
    fun fpsAtCriticalBoundaryMatchesGaugeCritical() {
        assertEquals(
            GaugeZone.CRITICAL,
            studioHealthZone(healthy.copy(fps = StatsThresholds.FPS_CRIT)),
        )
    }

    @Test
    fun criticalReadingTakesPrecedenceOverWarnings() {
        val state =
            healthy.copy(
                droppedPct = StatsThresholds.DROPPED_WARN_PCT,
                renderTimeMs = StatsThresholds.RENDER_CRIT_MS,
            )
        assertEquals(GaugeZone.CRITICAL, studioHealthZone(state))
    }
}
