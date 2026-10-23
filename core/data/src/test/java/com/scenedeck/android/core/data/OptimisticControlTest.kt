package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OptimisticControlTest {
    @Test
    fun twoFailedWritesRestoreConfirmedValueInEitherCompletionOrder() {
        for (olderFirst in listOf(false, true)) {
            val control = OptimisticControl(1.0, 0)
            control.begin(1, 0.25)
            control.begin(2, 0.75)
            if (olderFirst) {
                assertEquals(0.75, control.complete(1, false), 0.0)
                assertEquals(1.0, control.complete(2, false), 0.0)
            } else {
                assertEquals(0.25, control.complete(2, false), 0.0)
                assertEquals(1.0, control.complete(1, false), 0.0)
            }
        }
    }

    @Test
    fun successfulOlderWriteIsConfirmedEvenAfterNewerWriteFails() {
        for (olderFirst in listOf(false, true)) {
            val control = OptimisticControl(false, 0)
            control.begin(1, true)
            control.begin(2, false)
            if (olderFirst) {
                assertEquals(false, control.complete(1, true))
                assertEquals(true, control.complete(2, false))
            } else {
                assertEquals(true, control.complete(2, false))
                assertEquals(true, control.complete(1, true))
            }
        }
    }

    @Test
    fun newerObservationWinsAgainstOldFailuresSuccessesAndSnapshots() {
        val control = OptimisticControl(false, 0)
        control.begin(1, true)
        control.begin(2, false)
        assertEquals(true, control.observe(true, 3))
        assertEquals(true, control.complete(1, true))
        assertEquals(true, control.complete(2, false))
        assertEquals(true, control.observe(false, 0))
    }

    @Test
    fun discoveryPreservesNewerEditAndProvidesItsRollbackBaseline() {
        val control = OptimisticControl(1.0, 0)
        control.begin(2, 0.75)
        assertEquals(0.75, control.observe(0.5, 1), 0.0)
        assertEquals(0.5, control.complete(2, false), 0.0)
    }

    @Test
    fun newerSuccessfulWriteCannotBeRevertedByOlderAcknowledgment() {
        val control = OptimisticControl(1.0, 0)
        control.begin(1, 0.25)
        control.begin(2, 0.75)
        assertEquals(0.75, control.complete(2, true), 0.0)
        assertEquals(0.75, control.complete(1, true), 0.0)
    }
}
