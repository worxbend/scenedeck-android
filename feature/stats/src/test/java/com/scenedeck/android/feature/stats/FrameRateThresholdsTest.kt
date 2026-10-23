package com.scenedeck.android.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRateThresholdsTest {

    @Test
    fun thirtyFpsCanvas() {
        val t = FrameRateThresholds.forTargetFps(30f)
        assertEquals(30f, t.fpsMax, DELTA)
        assertEquals(27f, t.fpsWarn, DELTA)
        assertEquals(22.5f, t.fpsCrit, DELTA)
        assertEquals(33.333f, t.frameBudgetMs, DELTA)
        assertEquals(16.667f, t.renderWarnMs, DELTA)
        assertEquals(30f, t.renderCritMs, DELTA)
    }

    @Test
    fun sixtyFpsCanvas() {
        val t = FrameRateThresholds.forTargetFps(60f)
        assertEquals(60f, t.fpsMax, DELTA)
        assertEquals(54f, t.fpsWarn, DELTA)
        assertEquals(45f, t.fpsCrit, DELTA)
        assertEquals(16.667f, t.frameBudgetMs, DELTA)
        assertEquals(8.333f, t.renderWarnMs, DELTA)
        assertEquals(15f, t.renderCritMs, DELTA)
    }

    @Test
    fun ntscSixtyFpsCanvas() {
        val t = FrameRateThresholds.forTargetFps(60_000f / 1_001f)
        assertEquals(59.94f, t.fpsMax, DELTA)
        assertEquals(53.946f, t.fpsWarn, DELTA)
        assertEquals(44.955f, t.fpsCrit, DELTA)
        assertEquals(16.683f, t.frameBudgetMs, DELTA)
        assertEquals(8.342f, t.renderWarnMs, DELTA)
        assertEquals(15.015f, t.renderCritMs, DELTA)
    }

    @Test
    fun unknownOrInvalidCanvasFallsBackToSixty() {
        listOf(null, 0f, -30f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(60f, FrameRateThresholds.forTargetFps(it).targetFps, DELTA)
        }
    }

    @Test
    fun uiStateDerivesThresholdsFromTargetFps() {
        assertEquals(27f, StatsUiState(targetFps = 30f).frameRate.fpsWarn, DELTA)
        assertEquals(54f, StatsUiState().frameRate.fpsWarn, DELTA)
    }

    @Test
    fun frameRateTitleShowsKnownTarget() {
        assertEquals("Frame rate", frameRateTitle(null))
        assertEquals("Frame rate · target 30 fps", frameRateTitle(30f))
        assertEquals("Frame rate · target 59.94 fps", frameRateTitle(60_000f / 1_001f))
    }

    private companion object {
        const val DELTA = 1e-3f
    }
}
