package com.scenedeck.android.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendChartTest {

    @Test
    fun emptySeriesYieldsDefaultWindow() {
        val (min, max) = trendYScale(emptyList())
        assertEquals(0f, min, 1e-4f)
        assertEquals(1f, max, 1e-4f) // floor + minSpan
    }

    @Test
    fun peakPlusHeadroomScalesUp() {
        val (min, max) = trendYScale(listOf(10f, 40f, 25f), headroom = 0.1f)
        assertEquals(0f, min, 1e-4f)
        assertEquals(44f, max, 1e-4f) // 40 + 10 %
    }

    @Test
    fun floorAnchorsTheRange() {
        val (min, max) = trendYScale(listOf(5f, 8f), floor = 4f, headroom = 0f)
        assertEquals(4f, min, 1e-4f)
        assertEquals(8f, max, 1e-4f)
    }

    @Test
    fun minSpanGuardsFlatSeries() {
        val (min, max) = trendYScale(listOf(0f, 0f), minSpan = 2f)
        assertEquals(0f, min, 1e-4f)
        assertEquals(2f, max, 1e-4f)
    }

    @Test
    fun belowFloorPeakStillRespectsMinSpan() {
        val (_, max) = trendYScale(listOf(0.2f), floor = 1f, minSpan = 5f)
        assertEquals(6f, max, 1e-4f) // floor + minSpan wins over a peak under the floor
    }
}
