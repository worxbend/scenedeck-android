package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputSafetyGateTest {

    @Test
    fun defaultsConfirmStopsOnly() {
        val defaults = OutputSafety()
        assertEquals(OutputAction.PERFORM, OutputSafetyGate.streamAction(false, defaults))
        assertEquals(OutputAction.REQUIRE_CONFIRMATION, OutputSafetyGate.streamAction(true, defaults))
        assertEquals(OutputAction.PERFORM, OutputSafetyGate.recordAction(false, defaults))
        assertEquals(OutputAction.REQUIRE_CONFIRMATION, OutputSafetyGate.recordAction(true, defaults))
    }

    @Test
    fun confirmStartGatesStart() {
        val safety = OutputSafety(confirmStartStream = true, confirmStartRecord = true)
        assertEquals(OutputAction.REQUIRE_CONFIRMATION, OutputSafetyGate.streamAction(false, safety))
        assertEquals(OutputAction.REQUIRE_CONFIRMATION, OutputSafetyGate.recordAction(false, safety))
    }

    @Test
    fun allOffNeverConfirms() {
        val safety = OutputSafety(
            confirmStartStream = false,
            confirmStopStream = false,
            confirmStartRecord = false,
            confirmStopRecord = false,
        )
        assertEquals(OutputAction.PERFORM, OutputSafetyGate.streamAction(true, safety))
        assertEquals(OutputAction.PERFORM, OutputSafetyGate.recordAction(true, safety))
    }

    @Test
    fun allOnAlwaysConfirms() {
        val safety = OutputSafety(
            confirmStartStream = true,
            confirmStopStream = true,
            confirmStartRecord = true,
            confirmStopRecord = true,
        )
        listOf(true, false).forEach { active ->
            assertEquals(OutputAction.REQUIRE_CONFIRMATION, OutputSafetyGate.streamAction(active, safety))
            assertEquals(OutputAction.REQUIRE_CONFIRMATION, OutputSafetyGate.recordAction(active, safety))
        }
    }
}
