package com.scenedeck.android.feature.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualCamToggleTest {

    private val active = MutableStateFlow(false)
    private var toggles = 0
    private var failures = 0

    private fun TestScope.controller(toggle: suspend () -> Unit = { toggles++ }) =
        VirtualCamToggle(
            scope = backgroundScope,
            currentActive = { active.value },
            activeUpdates = active,
            toggle = toggle,
            onFailure = { failures++ },
            timeoutMs = 10_000L,
        )

    @Test
    fun startStaysPendingUntilObsReportsTheCameraActive() = runTest {
        val vcam = controller()
        vcam.request()
        runCurrent()
        assertEquals(VirtualCamPending.STARTING, vcam.pending.value)
        assertEquals(1, toggles)

        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(VirtualCamPending.STARTING, vcam.pending.value)

        active.value = true
        runCurrent()
        assertNull(vcam.pending.value)
    }

    @Test
    fun stopReportsStoppingAndTapsWhilePendingAreIgnored() = runTest {
        active.value = true
        val vcam = controller()
        vcam.request()
        vcam.request()
        runCurrent()
        assertEquals(VirtualCamPending.STOPPING, vcam.pending.value)
        assertEquals(1, toggles)

        active.value = false
        runCurrent()
        assertNull(vcam.pending.value)
    }

    @Test
    fun buttonLabelsDistinguishIdleActiveAndPending() {
        assertEquals(R.string.vcam_chip, virtualCamLabel(active = false, pending = null))
        assertEquals(R.string.vcam_stop_short, virtualCamLabel(active = true, pending = null))
        assertEquals(
            R.string.vcam_starting,
            virtualCamLabel(active = false, pending = VirtualCamPending.STARTING),
        )
        assertEquals(
            R.string.vcam_stopping,
            virtualCamLabel(active = true, pending = VirtualCamPending.STOPPING),
        )
        assertEquals(R.string.start_vcam_cd, virtualCamDescription(false, null))
        assertEquals(R.string.stop_vcam_cd, virtualCamDescription(true, null))
        assertEquals(R.string.vcam_name, virtualCamDescription(false, VirtualCamPending.STARTING))
        assertEquals(
            R.string.vcam_state_starting,
            virtualCamStateDescription(VirtualCamPending.STARTING),
        )
        assertEquals(
            R.string.vcam_state_stopping,
            virtualCamStateDescription(VirtualCamPending.STOPPING),
        )
        assertEquals(null, virtualCamStateDescription(null))
    }

    @Test
    fun failureClearsPendingAndReportsOnce() = runTest {
        val vcam = controller(toggle = { error("refused") })
        vcam.request()
        runCurrent()
        assertNull(vcam.pending.value)
        assertEquals(1, failures)
    }

    @Test
    fun missingConfirmationTimesOutWithoutRetrying() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vcam = controller(toggle = { toggles++.also { gate.complete(Unit) } })
        vcam.request()
        runCurrent()
        gate.await()
        advanceTimeBy(10_001L)
        runCurrent()
        assertNull(vcam.pending.value)
        assertEquals(1, toggles)
        assertEquals(0, failures)
    }
}
