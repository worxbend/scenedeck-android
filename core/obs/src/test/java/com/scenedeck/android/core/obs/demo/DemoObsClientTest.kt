package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.RecordingPhase
import com.scenedeck.android.core.obs.ObsNotConnectedException
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.ObsWorkspaceChangedException
import com.scenedeck.android.core.obs.ScreenshotRequest
import com.scenedeck.android.core.obs.withSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoObsClientTest {
    @Test
    fun rejectsNetworkTargetsAndCredentialsWithoutContactingTheServer() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = client()
            client.connect("127.0.0.1", server.port, "private-password")
            assertTrue(client.connectionState.value is ConnectionState.Failed)
            assertEquals(0, server.requestCount)
            assertFalse(client.connectionState.value.toString().contains("private-password"))
            failure<ObsNotConnectedException> { client.startStream() }
            client.connect(DemoObsClient.HOST, password = "private-password")
            assertTrue(client.connectionState.value is ConnectionState.Failed)
            assertEquals(0, server.requestCount)
            client.connect(DemoObsClient.HOST)
            assertTrue(client.connectionState.value is ConnectionState.Ready)
            assertFalse(client.retryNow())
        }
    }

    @Test
    fun sceneAndStudioControlsPreserveLiteralTargetsAndEmitCapturedIdentity() = runTest {
        val client = connected()
        val events = mutableListOf<ObsSessionEvent>()
        backgroundScope.launch { client.eventsWithContext.collect(events::add) }
        runCurrent()
        assertEquals(5, client.getSceneList().scenes.size)
        failure<ObsRequestFailedException> { client.triggerStudioModeTransition() }
        client.setStudioModeEnabled(true)
        client.setCurrentPreviewScene(DemoCatalog.INTERVIEW)
        client.triggerStudioModeTransition()
        assertEquals(DemoCatalog.INTERVIEW, client.getCurrentProgramScene())
        assertEquals(DemoCatalog.CAMERA, client.getCurrentPreviewScene())
        runCurrent()
        val ready = client.connectionState.value as ConnectionState.Ready
        assertTrue(events.all { it.token == ready.token })
        assertTrue(
            events.any { it.event == ObsEvent.CurrentProgramSceneChanged(DemoCatalog.INTERVIEW) }
        )
        assertTrue(
            events.any { it.event == ObsEvent.CurrentPreviewSceneChanged(DemoCatalog.CAMERA) }
        )
        assertEquals(
            "ResourceNotFound",
            failure<ObsRequestFailedException> {
                    client.setCurrentProgramScene("${DemoCatalog.CAMERA} ")
                }
                .statusCode,
        )
        assertEquals(DemoCatalog.INTERVIEW, client.getCurrentProgramScene())
    }

    @Test
    fun freshSessionRejectsDeferredOldActionsAndResetsEveryOutput() = runTest {
        val client = connected()
        client.startRecord()
        client.toggleVirtualCam()
        val first = (client.connectionState.value as ConnectionState.Ready).token
        val release = CompletableDeferred<Unit>()
        val delayed = async {
            failure<ObsWorkspaceChangedException> {
                client.withSession(first) {
                    release.await()
                    startStream()
                }
            }
        }
        runCurrent()
        client.disconnect()
        client.connect(DemoObsClient.HOST)
        release.complete(Unit)
        delayed.await()
        assertNotEquals(first, (client.connectionState.value as ConnectionState.Ready).token)
        assertFalse(client.getStreamStatus().active)
        assertFalse(client.getRecordStatus().active)
        assertFalse(client.getVirtualCamStatus())
        assertEquals(0L, client.getRecordStatus().durationMs)
    }

    @Test
    fun recordingPauseAndIdempotentTargetsKeepIndependentOutputClocks() = runTest {
        val client = connected()
        val events = mutableListOf<ObsSessionEvent>()
        backgroundScope.launch { client.eventsWithContext.collect(events::add) }
        runCurrent()
        client.startStream()
        client.startRecord()
        advanceTimeBy(2_000)
        client.startRecord()
        client.pauseRecord()
        advanceTimeBy(3_000)
        val paused = client.getRecordStatus()
        assertTrue(paused.paused)
        assertEquals(2_000L, paused.durationMs)
        assertEquals(5_000L, client.getStreamStatus().durationMs)
        failure<ObsRequestFailedException> { client.splitRecordFile() }
        client.resumeRecord()
        advanceTimeBy(1_000)
        assertEquals(3_000L, client.getRecordStatus().durationMs)
        client.splitRecordFile()
        client.createRecordChapter("Demo mark")
        client.stopRecord()
        runCurrent()
        val recording = events.mapNotNull { it.event as? ObsEvent.RecordStateChanged }
        assertEquals(
            listOf(
                RecordingPhase.STARTED,
                RecordingPhase.PAUSED,
                RecordingPhase.RESUMED,
                RecordingPhase.STOPPED,
            ),
            recording.map { it.phase },
        )
        assertTrue(recording.all { it.outputPath == null })
        assertEquals("00:00:06", client.getStreamStatus().timecode)
        failure<ObsRequestFailedException> { client.createRecordChapter(null) }
    }

    @Test
    fun metersAreStampedAndMuteKeepsPreFaderSignalWithoutInventingPostFaderAudio() = runTest {
        val client = connected()
        val readings = mutableListOf<ObsSessionMeters>()
        val subscription = backgroundScope.launch {
            client.metersWithContext.collect(readings::add)
        }
        runCurrent()
        val initial =
            readings.last().readings.first { it.inputName == DemoCatalog.MIC }.channels.single()
        assertTrue(initial.peakMul > 0f)
        client.setInputMute(DemoCatalog.MIC, true)
        advanceTimeBy(100)
        runCurrent()
        val muted =
            readings.last().readings.first { it.inputName == DemoCatalog.MIC }.channels.single()
        assertEquals(0f, muted.peakMul, 0f)
        assertTrue(muted.inputPeakMul > 0f)
        val oldToken = readings.last().token
        client.connect(DemoObsClient.HOST)
        runCurrent()
        assertNotEquals(oldToken, readings.last().token)
        client.disconnect()
        runCurrent()
        val count = readings.size
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(count, readings.size)
        subscription.cancel()
    }

    @Test
    fun mediaUsesObservedPauseAndClampedSeekPositions() = runTest {
        val client = connected()
        failure<ObsRequestFailedException> { client.setMediaInputCursor(DemoCatalog.CLIP, 100) }
        client.triggerMediaInputAction(DemoCatalog.CLIP, MediaActionKind.PLAY)
        advanceTimeBy(2_000)
        client.triggerMediaInputAction(DemoCatalog.CLIP, MediaActionKind.PAUSE)
        advanceTimeBy(5_000)
        assertEquals(2_000L, client.getMediaInputStatus(DemoCatalog.CLIP).cursorMs)
        client.setMediaInputCursor(DemoCatalog.CLIP, -20)
        assertEquals(0L, client.getMediaInputStatus(DemoCatalog.CLIP).cursorMs)
        client.setMediaInputCursor(DemoCatalog.CLIP, Long.MAX_VALUE)
        assertEquals(DemoMedia.DURATION_MS, client.getMediaInputStatus(DemoCatalog.CLIP).cursorMs)
        client.triggerMediaInputAction(DemoCatalog.CLIP, MediaActionKind.RESTART)
        advanceTimeBy(DemoMedia.DURATION_MS + 1)
        assertEquals(MediaStateKind.ENDED, client.getMediaInputStatus(DemoCatalog.CLIP).state)
        failure<ObsRequestFailedException> {
            client.triggerMediaInputAction(DemoCatalog.CLIP, MediaActionKind.NEXT)
        }
        failure<ObsRequestFailedException> { client.getMediaInputStatus(DemoCatalog.MIC) }
    }

    @Test
    fun capabilitiesAndAudioControlsAreRealSimulationBehaviorWithInvalidValuesRejected() = runTest {
        val client = connected()
        assertEquals(ObsControl.entries.toSet(), client.getVersion().capabilities.controls)
        assertEquals(60.0, client.getVideoSettings().fps!!, 0.0)
        assertEquals(60.0, client.getStats().activeFps, 0.0)
        client.setInputVolume(DemoCatalog.MIC, 0.5)
        client.setInputAudioBalance(DemoCatalog.MIC, 0.2)
        client.setInputAudioSyncOffset(DemoCatalog.MIC, 75)
        client.setInputAudioMonitorType(DemoCatalog.MIC, MonitorTypeKind.MONITOR_ONLY)
        assertEquals(0.5, client.getInputVolume(DemoCatalog.MIC), 0.0)
        assertEquals(0.2, client.getInputAudioBalance(DemoCatalog.MIC), 0.0)
        assertEquals(75, client.getInputAudioSyncOffset(DemoCatalog.MIC))
        assertEquals(MonitorTypeKind.MONITOR_ONLY, client.getInputAudioMonitorType(DemoCatalog.MIC))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            failure<ObsRequestFailedException> { client.setInputVolume(DemoCatalog.MIC, it) }
        }
        failure<ObsRequestFailedException> { client.getInputMute(DemoCatalog.VIDEO) }
        failure<ObsRequestFailedException> { client.setInputMute("missing", true) }
        assertEquals(0.5, client.getInputVolume(DemoCatalog.MIC), 0.0)
    }

    @Test
    fun transitionsSceneItemsAndConfigRemainInsideTheFixedCollection() = runTest {
        val client = connected()
        client.setCurrentSceneTransitionDuration(500)
        assertEquals(500, client.getCurrentSceneTransition().durationMs)
        client.setCurrentSceneTransition("Cut")
        assertNull(client.getCurrentSceneTransition().durationMs)
        failure<ObsRequestFailedException> { client.setCurrentSceneTransitionDuration(200) }
        client.setSceneItemEnabled(DemoCatalog.CAMERA, 1, false)
        assertFalse(client.getSceneItemEnabled(DemoCatalog.CAMERA, 1))
        failure<ObsRequestFailedException> {
            client.setSceneItemEnabled(DemoCatalog.CAMERA, 9, true)
        }
        failure<ObsRequestFailedException> { client.setCurrentSceneCollection("real collection") }
        failure<ObsRequestFailedException> { client.setCurrentProfile("real profile") }
        assertEquals(DemoCatalog.COLLECTION, client.getSceneCollectionList().currentCollection)
        assertEquals(DemoCatalog.PROFILE, client.getProfileList().currentProfile)
        assertEquals(5, client.getSceneList().scenes.size)
    }

    @Test
    fun screenshotBoundaryIsLocalBoundedAndDoesNotExposeProviderArray() = runTest {
        var bytes = byteArrayOf(1, 2, 3)
        var requested: ScreenshotRequest? = null
        val client =
            DemoObsClient(backgroundScope, { testScheduler.currentTime }) {
                requested = it
                bytes
            }
        client.connect(DemoObsClient.HOST)
        val request = ScreenshotRequest(DemoCatalog.CAMERA)
        val captured = client.getSourceScreenshot(request)
        captured[0] = 9
        assertEquals(1.toByte(), bytes[0])
        assertEquals(request, requested)
        bytes = ByteArray(ScreenshotRequest.MAX_IMAGE_BYTES + 1)
        failure<ObsRequestFailedException> { client.getSourceScreenshot(request) }
        failure<ObsRequestFailedException> {
            client.getSourceScreenshot(request.copy(sourceName = "real scene"))
        }
    }

    @Test
    fun ownerCancellationStopsSimulationAndCannotRestartItsClosedScope() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val client = DemoObsClient(owner, { testScheduler.currentTime })
        client.connect(DemoObsClient.HOST)
        owner.cancel()
        runCurrent()
        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        failure<kotlinx.coroutines.CancellationException> { client.connect(DemoObsClient.HOST) }
        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
    }

    private fun TestScope.client() = DemoObsClient(backgroundScope, { testScheduler.currentTime })

    private suspend fun TestScope.connected() = client().also { it.connect(DemoObsClient.HOST) }
}

private suspend inline fun <reified T : Throwable> failure(action: () -> Unit): T {
    val failure = runCatching(action).exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName}, got $failure", failure is T)
    return failure as T
}
