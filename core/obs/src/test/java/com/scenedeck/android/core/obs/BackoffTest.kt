package com.scenedeck.android.core.obs

import com.scenedeck.android.core.obs.internal.exponentialBackoffMillis
import org.junit.Assert.assertEquals
import org.junit.Test

internal class BackoffTest {

    @Test
    fun backoff_isExponentialAndCappedAt30s() {
        assertEquals(1_000L, exponentialBackoffMillis(1))
        assertEquals(2_000L, exponentialBackoffMillis(2))
        assertEquals(4_000L, exponentialBackoffMillis(3))
        assertEquals(8_000L, exponentialBackoffMillis(4))
        assertEquals(16_000L, exponentialBackoffMillis(5))
        assertEquals(30_000L, exponentialBackoffMillis(6))
        assertEquals(30_000L, exponentialBackoffMillis(10))
    }

    @Test
    fun backoff_rejectsNonPositiveAttempts() {
        assertFails<IllegalArgumentException> { exponentialBackoffMillis(0) }
    }
}
