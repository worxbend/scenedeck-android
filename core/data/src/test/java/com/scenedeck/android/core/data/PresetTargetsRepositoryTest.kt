package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsInputSummary
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetTargetsRepositoryTest {
    private val client = DirectoryClient()
    private val workspace = MutableWorkspaceContext()
    private val repository = PresetTargetsRepository(client, workspace)

    @Test
    fun subscribesBeforeReadAndNewEventCancelsObsoleteDirectory() = runTest {
        val pending = CompletableDeferred<Unit>()
        var canceled = false
        client.read = {
            assertEquals(1, client.eventsWithContext.subscriptionCount.value)
            try {
                pending.await()
            } finally {
                canceled = true
            }
            listOf(ObsInputSummary("Obsolete", "audio"))
        }
        val values = mutableListOf<PresetTargets>()
        backgroundScope.launch { repository.state.collect { values += it } }
        runCurrent()
        client.read = {
            listOf(
                ObsInputSummary("z", "video"),
                ObsInputSummary("A", "audio"),
                ObsInputSummary("A", "duplicate"),
            )
        }
        client.eventsWithContext.emit(
            ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.InputRemoved("Obsolete"))
        )
        runCurrent()
        assertTrue(canceled)
        val ready = values.last() as PresetTargets.Ready
        assertEquals(listOf("A", "z"), ready.inputs.map { it.name })
        assertEquals("audio", ready.inputs.first().kind)
        pending.complete(Unit)
        runCurrent()
        assertEquals(ready, values.last())
    }

    @Test
    fun failureAndOversizeAreUnknownAndRetryDoesNotRequireAnObsEvent() = runTest {
        val values = mutableListOf<PresetTargets>()
        client.read = { error("private transport") }
        backgroundScope.launch { repository.state.collect { values += it } }
        runCurrent()
        assertEquals(PresetTargets.Failed(TEST_WORKSPACE), values.last())
        client.read = { List(2_001) { ObsInputSummary("Input $it", "audio") } }
        repository.retry()
        runCurrent()
        assertEquals(PresetTargets.Failed(TEST_WORKSPACE), values.last())
        client.read = { emptyList() }
        repository.retry()
        runCurrent()
        assertEquals(PresetTargets.Ready(TEST_WORKSPACE, emptyList()), values.last())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(3, client.calls) // The editor does not poll OBS.
    }

    @Test
    fun otherSessionEventsAreIgnoredAndReplacementClearsInFlightRead() = runTest {
        val values = mutableListOf<PresetTargets>()
        backgroundScope.launch { repository.state.collect { values += it } }
        runCurrent()
        client.eventsWithContext.emit(
            ObsSessionEvent(ObsSessionToken(5, 0), ObsEvent.InputRemoved("Mic"))
        )
        runCurrent()
        assertEquals(1, client.calls)
        var active = false
        client.read = {
            active = true
            try {
                awaitCancellation()
            } finally {
                active = false
            }
        }
        repository.retry()
        runCurrent()
        assertTrue(active)
        workspace.unavailable()
        runCurrent()
        assertFalse(active)
        assertEquals(PresetTargets.Unavailable, values.last())
        client.read = { listOf(ObsInputSummary("New input", "audio")) }
        workspace.select(TEST_WORKSPACE.copy(key = WorkspaceKey(8, "Second")))
        runCurrent()
        assertEquals("Second", (values.last() as PresetTargets.Ready).origin.key.collectionName)
    }

    @Test
    fun timeoutIsFailedAndStoppingCollectionCancelsItsRead() = runTest {
        var active = false
        client.read = {
            active = true
            try {
                awaitCancellation()
            } finally {
                active = false
            }
        }
        val values = mutableListOf<PresetTargets>()
        val collecting = backgroundScope.launch { repository.state.collect { values += it } }
        runCurrent()
        advanceTimeBy(12_000)
        runCurrent()
        assertFalse(active)
        assertEquals(PresetTargets.Failed(TEST_WORKSPACE), values.last())
        repository.retry()
        runCurrent()
        assertTrue(active)
        collecting.cancel()
        runCurrent()
        assertFalse(active)
        assertEquals(0, client.eventsWithContext.subscriptionCount.value)
    }
}

private class DirectoryClient : FakeObsClient() {
    override val eventsWithContext = MutableSharedFlow<ObsSessionEvent>()
    var calls = 0
    var read: suspend () -> List<ObsInputSummary> = { emptyList() }

    init {
        setReady()
    }

    override suspend fun getInputList(): List<ObsInputSummary> {
        calls++
        return read()
    }
}
