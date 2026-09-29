package com.scenedeck.android.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StatGaugeTest {

    @Test
    fun risingBoundaries() {
        // Dropped-frame % semantics: warn 1, crit 5 (docs/FEATURE_SPEC.md §5).
        assertEquals(GaugeZone.NORMAL, classifyGaugeZone(0f, 1f, 5f))
        assertEquals(GaugeZone.NORMAL, classifyGaugeZone(0.99f, 1f, 5f))
        assertEquals(GaugeZone.WARNING, classifyGaugeZone(1f, 1f, 5f))
        assertEquals(GaugeZone.WARNING, classifyGaugeZone(4.99f, 1f, 5f))
        assertEquals(GaugeZone.CRITICAL, classifyGaugeZone(5f, 1f, 5f))
        assertEquals(GaugeZone.CRITICAL, classifyGaugeZone(100f, 1f, 5f))
    }

    @Test
    fun fallingBoundaries() {
        // FPS semantics: warn at/below 54, crit at/below 45 (low FPS is bad).
        assertEquals(GaugeZone.NORMAL, classifyGaugeZone(60f, 54f, 45f, GaugeDirection.FALLING))
        assertEquals(GaugeZone.NORMAL, classifyGaugeZone(54.01f, 54f, 45f, GaugeDirection.FALLING))
        assertEquals(GaugeZone.WARNING, classifyGaugeZone(54f, 54f, 45f, GaugeDirection.FALLING))
        assertEquals(GaugeZone.WARNING, classifyGaugeZone(45.01f, 54f, 45f, GaugeDirection.FALLING))
        assertEquals(GaugeZone.CRITICAL, classifyGaugeZone(45f, 54f, 45f, GaugeDirection.FALLING))
        assertEquals(GaugeZone.CRITICAL, classifyGaugeZone(0f, 54f, 45f, GaugeDirection.FALLING))
    }

    @Test
    fun fractionClampsAndGuards() {
        assertEquals(0f, gaugeFraction(0f, 0f, 60f), 1e-4f)
        assertEquals(0.5f, gaugeFraction(30f, 0f, 60f), 1e-4f)
        assertEquals(1f, gaugeFraction(60f, 0f, 60f), 1e-4f)
        assertEquals(1f, gaugeFraction(90f, 0f, 60f), 1e-4f) // above max clamps
        assertEquals(0f, gaugeFraction(-5f, 0f, 60f), 1e-4f) // below min clamps
        assertEquals(0f, gaugeFraction(1f, 5f, 5f), 1e-4f) // degenerate range
    }
}
