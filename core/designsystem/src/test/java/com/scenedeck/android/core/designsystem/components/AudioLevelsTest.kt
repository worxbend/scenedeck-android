package com.scenedeck.android.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLevelsTest {

    @Test
    fun mulToDbBoundaries() {
        assertEquals(0f, mulToDb(1f), 1e-4f)
        assertEquals(-6.0206f, mulToDb(0.5f), 1e-3f)
        assertEquals(-60f, mulToDb(0.001f), 1e-4f)
        assertEquals(-60f, mulToDb(0.0001f), 1e-4f) // clamped below floor
        assertEquals(-60f, mulToDb(0f), 1e-4f) // zero = floor/-inf
    }

    @Test
    fun dbToMulBoundaries() {
        assertEquals(1f, dbToMul(0f), 1e-4f)
        assertEquals(0.5012f, dbToMul(-6f), 1e-3f)
        assertEquals(0f, dbToMul(-60f), 1e-6f) // floor maps to 0
        assertEquals(0f, dbToMul(-80f), 1e-6f)
    }

    @Test
    fun fractionMapping() {
        assertEquals(1f, dbToFraction(0f), 1e-4f)
        assertEquals(0.5f, dbToFraction(-30f), 1e-4f)
        assertEquals(0f, dbToFraction(-60f), 1e-4f)
        assertEquals(1f, dbToFraction(6f), 1e-4f) // clamped
    }

    @Test
    fun formatting() {
        assertEquals("0.0 dB", formatDb(0f))
        assertEquals("-6.0 dB", formatDb(-6.02f))
        assertEquals("-inf dB", formatDb(-60f))
        assertEquals("-inf dB", formatDb(-120f))
    }
}
