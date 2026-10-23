package com.scenedeck.android.connectivity

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTransitionsTest {

    private var foregrounds = 0
    private val transitions = ForegroundTransitions(onForeground = { foregrounds++ })

    @Test
    fun firstStartedActivityIsAForegroundTransition() {
        transitions.onActivityStarted()

        assertEquals(1, foregrounds)
    }

    @Test
    fun activitySwitchesInsideTheAppDoNotRetry() {
        transitions.onActivityStarted()
        // A second activity starts before the first stops.
        transitions.onActivityStarted()
        transitions.onActivityStopped()

        assertEquals(1, foregrounds)
    }

    @Test
    fun returningFromBackgroundRetriesAgain() {
        transitions.onActivityStarted()
        transitions.onActivityStopped()
        transitions.onActivityStarted()

        assertEquals(2, foregrounds)
    }

    @Test
    fun unbalancedStopNeverSuppressesTheNextForeground() {
        transitions.onActivityStopped()
        transitions.onActivityStarted()

        assertEquals(1, foregrounds)
    }
}
