package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetStepProbeTest {
    private val client = PresetProbeClient()
    private val context = MutableWorkspaceContext()
    private val probe = PresetStepProbe(client, context)
    private val policy = UserSettings().presetPolicy()
    private val mute = PresetStep.Mute("Mic", true)

    @Test
    fun unsupportedLockedAndExcludedInputsMakeNoRead(): Unit = runTest {
        assertEquals(
            PresetCheck.LOCKED,
            probe.inspect(TEST_WORKSPACE, mute, policy.copy(lockedInputs = setOf("Mic"))),
        )
        assertEquals(
            PresetCheck.NOT_ALLOWED,
            probe.inspect(TEST_WORKSPACE, mute, policy.copy(allowedInputs = setOf("Other"))),
        )
        client.setReady()
        assertEquals(PresetCheck.UNSUPPORTED, probe.inspect(TEST_WORKSPACE, mute, policy))
        assertTrue(client.reads.isEmpty())
    }

    @Test
    fun scenesMustExistAndPreviewRequiresStudioMode(): Unit = runTest {
        val scene = PresetStep.Scene("Missing", PresetSceneDestination.PROGRAM)
        assertEquals(PresetCheck.MISSING, probe.inspect(TEST_WORKSPACE, scene, policy))
        assertEquals(
            PresetCheck.CURRENTLY_MATCHES,
            probe.inspect(TEST_WORKSPACE, scene.copy(name = "Camera"), policy),
        )
        val preview = scene.copy(name = "Camera", destination = PresetSceneDestination.PREVIEW)
        assertEquals(PresetCheck.STUDIO_OFF, probe.inspect(TEST_WORKSPACE, preview, policy))
        client.studioModeEnabled = true
        assertEquals(PresetCheck.CURRENTLY_MATCHES, probe.inspect(TEST_WORKSPACE, preview, policy))
        assertTrue(client.sceneSwitchCalls.isEmpty())
    }

    @Test
    fun invalidReadingsAndUnsupportedMediaStatesNeverBecomeReady(): Unit = runTest {
        client.volume = Double.NaN
        assertEquals(
            PresetCheck.UNKNOWN,
            probe.inspect(TEST_WORKSPACE, PresetStep.Volume("Music", 0.5), policy),
        )
        val media = PresetStep.Media("Clip", PresetMediaState.PLAYING)
        listOf(
                MediaStateKind.OPENING,
                MediaStateKind.BUFFERING,
                MediaStateKind.ERROR,
                MediaStateKind.NONE,
            )
            .forEach {
                client.media = it
                assertEquals(PresetCheck.NOT_READY, probe.inspect(TEST_WORKSPACE, media, policy))
            }
        client.media = MediaStateKind.PAUSED
        assertEquals(PresetCheck.READY, probe.inspect(TEST_WORKSPACE, media, policy))
    }

    @Test
    fun quietVolumesAndSilenceAreNotMistakenForTheRequestedTarget(): Unit = runTest {
        client.volume = 0.0004
        assertEquals(
            PresetCheck.READY,
            probe.inspect(TEST_WORKSPACE, PresetStep.Volume("Music", 0.0), policy),
        )
        client.volume = 0.0014
        assertEquals(
            PresetCheck.READY,
            probe.inspect(TEST_WORKSPACE, PresetStep.Volume("Music", 0.001), policy),
        )
        client.volume = 0.2500000001
        assertEquals(
            PresetCheck.CURRENTLY_MATCHES,
            probe.inspect(TEST_WORKSPACE, PresetStep.Volume("Music", 0.25), policy),
        )
    }

    @Test
    fun readErrorsDistinguishMissingFromUnknownWithoutServerDetails(): Unit = runTest {
        client.beforeRead = {
            throw ObsRequestFailedException("GetInputMute", "ResourceNotFound", "private")
        }
        assertEquals(PresetCheck.MISSING, probe.inspect(TEST_WORKSPACE, mute, policy))
        client.beforeRead = {
            throw ObsRequestFailedException("GetInputMute", "InvalidResourceState", "private")
        }
        assertEquals(PresetCheck.NOT_READY, probe.inspect(TEST_WORKSPACE, mute, policy))
        client.beforeRead = { error("private server details") }
        assertEquals(PresetCheck.UNKNOWN, probe.inspect(TEST_WORKSPACE, mute, policy))
    }

    @Test
    fun reconnectingStreamIsNotAlreadyStoppedAndContradictoryRecordingIsUnknown(): Unit = runTest {
        val stop = PresetStep.Output(PresetOutput.STREAM, false)
        client.streamStatusResponse =
            client.streamStatusResponse.copy(active = false, reconnecting = true)
        assertEquals(PresetCheck.READY, probe.inspect(TEST_WORKSPACE, stop, policy))
        assertEquals(
            PresetCheck.NOT_READY,
            probe.inspect(TEST_WORKSPACE, stop.copy(active = true), policy),
        )
        client.recordStatusResponse =
            client.recordStatusResponse.copy(active = false, paused = true)
        assertEquals(
            PresetCheck.UNKNOWN,
            probe.inspect(TEST_WORKSPACE, PresetStep.Output(PresetOutput.RECORDING, false), policy),
        )
    }

    @Test
    fun replacedWorkspaceCannotPublishTheCompletedReading(): Unit = runTest {
        val pending = CompletableDeferred<Unit>()
        client.beforeRead = { pending.await() }
        val reading = async { runCatching { probe.inspect(TEST_WORKSPACE, mute, policy) } }
        runCurrent()
        context.unavailable()
        pending.complete(Unit)
        assertTrue(reading.await().exceptionOrNull() is WorkspaceChangedException)
        context.select(TEST_WORKSPACE)
        client.setReady(sessionId = 2)
        assertTrue(
            runCatching { probe.inspect(TEST_WORKSPACE, mute, policy) }.exceptionOrNull()
                is WorkspaceChangedException
        )
    }

    @Test
    fun timedOutReadIsUnknownAndCallerCancellationIsNotSwallowed(): Unit = runTest {
        var active = false
        client.beforeRead = {
            active = true
            try {
                awaitCancellation()
            } finally {
                active = false
            }
        }
        val reading = async { probe.inspect(TEST_WORKSPACE, mute, policy) }
        runCurrent()
        assertTrue(active)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(PresetCheck.UNKNOWN, reading.await())
        assertFalse(active)
        val canceled = async { probe.inspect(TEST_WORKSPACE, mute, policy) }
        runCurrent()
        canceled.cancelAndJoin()
        assertTrue(canceled.isCancelled)
        assertFalse(active)
    }
}
