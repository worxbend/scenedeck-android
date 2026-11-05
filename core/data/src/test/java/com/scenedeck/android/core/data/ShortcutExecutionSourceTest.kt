package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ShortcutExecutionSourceTest : ShortcutExecutionTestFixture() {
    @Test
    fun namedSceneUsesLiteralIdentityAndFollowsStudioMode(): Unit = runBlocking {
        val binding = saved(ShortcutAction.Scene(" Camera "))
        val controls = controls()
        controls.runner.run(origin, binding)
        assertEquals(ShortcutOutcome.APPLIED, controls.runner.state.value!!.outcome)
        client.studioModeEnabled = true
        client.previewScene = "Other"
        controls.runner.run(origin, binding)
        assertEquals(ShortcutOutcome.APPLIED, controls.runner.state.value!!.outcome)
        assertEquals(listOf("program: Camera ", "preview: Camera "), client.calls)
    }

    @Test
    fun takeUsesCapturedPreviewAndAllowsTheNormalProgramPreviewExchange(): Unit = runBlocking {
        client.studioModeEnabled = true
        val controls = controls()
        controls.runner.run(origin, saved(ShortcutAction.TakePreview))
        assertEquals(ShortcutOutcome.APPLIED, controls.runner.state.value!!.outcome)
        assertEquals(ShortcutTarget.Take(" Camera "), controls.runner.state.value!!.target)
        assertEquals(" Camera ", client.program)
        assertEquals("Other", client.previewScene)
        assertEquals(listOf("take: Camera "), client.calls)
    }

    @Test
    fun sceneModeChangedAfterPreparationCannotBecomeADifferentDestination(): Unit = runBlocking {
        val binding = saved(ShortcutAction.Scene(" Camera "))
        val controls = controls()
        val plan = controls.source.prepare(origin, binding)
        client.studioModeEnabled = true
        assertIssue(ShortcutIssue.TARGET_CHANGED) {
            controls.source.write(plan) { error("Must not dispatch") }
        }
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun takeRequiresStudioAndTheSameFreshPreview(): Unit = runBlocking {
        val binding = saved(ShortcutAction.TakePreview)
        val source = controls().source
        assertIssue(ShortcutIssue.STUDIO_OFF) { source.prepare(origin, binding) }
        client.studioModeEnabled = true
        val plan = source.prepare(origin, binding)
        client.previewScene = "Other"
        assertIssue(ShortcutIssue.TARGET_CHANGED) {
            source.write(plan) { error("Must not dispatch") }
        }
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun missingOrUnsupportedTargetsAndLockedOrExcludedInputsNeverWrite(): Unit = runBlocking {
        val binding = saved(ShortcutAction.Mute("Mic", true))
        val controls = controls()
        settings.setInputLocked("Mic", true)
        assertIssue(ShortcutIssue.LOCKED) { controls.source.prepare(origin, binding) }
        settings.setInputLocked("Mic", false)
        settings.setAudioAllowList(setOf("Other"))
        assertIssue(ShortcutIssue.NOT_ALLOWED) { controls.source.prepare(origin, binding) }
        settings.setAudioAllowList(emptySet())
        client.muteFailure =
            ObsRequestFailedException("GetInputMute", "ResourceNotFound", "private")
        assertIssue(ShortcutIssue.MISSING) { controls.source.prepare(origin, binding) }
        client.muteFailure =
            ObsRequestFailedException("GetInputMute", "InvalidResourceState", "private")
        assertIssue(ShortcutIssue.NOT_READY) { controls.source.prepare(origin, binding) }
        client.muteFailure = null
        client.setReady(
            capabilities =
                ObsCapabilities(controls = ObsControl.entries.toSet() - ObsControl.INPUT_MUTE)
        )
        assertIssue(ShortcutIssue.UNSUPPORTED) { controls.source.prepare(origin, binding) }
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun changedDefinitionPolicyAndSessionRejectAStoredPlanAtTheWriteBoundary(): Unit = runBlocking {
        val binding = saved(ShortcutAction.Mute("Mic", true))
        val source = controls().source
        val plan = source.prepare(origin, binding)
        settings.setInputLocked("Mic", true)
        assertIssue(ShortcutIssue.POLICY_CHANGED) {
            source.write(plan) { error("Must not dispatch") }
        }
        settings.setInputLocked("Mic", false)
        repository.save(origin, binding, binding.draft().copy(enabled = false))
        assertIssue(ShortcutIssue.BINDING_CHANGED) {
            source.write(plan) { error("Must not dispatch") }
        }
        context.select(origin.copy(token = ObsSessionToken(3)))
        assertIssue(ShortcutIssue.WORKSPACE_CHANGED) {
            source.write(plan) { error("Must not dispatch") }
        }
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun offProgramMuteUsesExistingMixerControlsAndConfirmedState(): Unit = runBlocking {
        val controls = controls()
        controls.runner.run(origin, saved(ShortcutAction.Mute("Off program", true)))
        assertEquals(ShortcutOutcome.APPLIED, controls.runner.state.value!!.outcome)
        assertEquals(listOf("mute:Off program:true"), client.calls)
    }

    @Test
    fun outputsUseExplicitTargetsAndOneUseSafetyConsent(): Unit = runBlocking {
        client.streamStatusResponse = client.streamStatusResponse.copy(active = true)
        val binding = saved(ShortcutAction.Stream(false))
        val controls = controls()
        val running = async { controls.runner.run(origin, binding) }
        val pending =
            withTimeout(5_000) {
                controls.runner.state.first { it?.phase == ShortcutPhase.CONFIRMATION }!!
            }
        assertTrue(client.calls.isEmpty())
        assertTrue(controls.runner.confirm(pending.id))
        running.await()
        assertEquals(ShortcutOutcome.APPLIED, controls.runner.state.value!!.outcome)
        controls.runner.run(origin, binding)
        assertEquals(ShortcutOutcome.ALREADY_MATCHED, controls.runner.state.value!!.outcome)
        assertEquals(listOf("stream:false"), client.calls)
    }

    @Test
    fun changedPolicyWhileConfirmationIsOpenCancelsConsent(): Unit = runBlocking {
        client.recordStatusResponse = client.recordStatusResponse.copy(active = true)
        val controls = controls()
        val binding = saved(ShortcutAction.Recording(false))
        val running = async { controls.runner.run(origin, binding) }
        withTimeout(5_000) {
            controls.runner.state.first { it?.phase == ShortcutPhase.CONFIRMATION }
        }
        settings.setConfirmStopRecord(false)
        withTimeout(5_000) { running.await() }
        assertEquals(ShortcutIssue.POLICY_CHANGED, controls.runner.state.value!!.issue)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun busyRecordingToolRejectsShortcutWithoutCancelingTheTool(): Unit = runBlocking {
        client.recordStatusResponse = client.recordStatusResponse.copy(active = true)
        val controls = controls()
        val binding = saved(ShortcutAction.Recording(false))
        val target =
            withTimeout(5_000) {
                controls.recording.state
                    .first { it.origin != null && it.record?.active == true }
                    .origin!!
            }
        val tool = launch { controls.recording.perform(target, RecordingAction.SPLIT) }
        withTimeout(5_000) { client.toolEntered.await() }
        try {
            controls.runner.run(origin, binding)
            assertEquals(ShortcutIssue.BUSY, controls.runner.state.value!!.issue)
            assertEquals(ShortcutOutcome.NOT_RUN, controls.runner.state.value!!.outcome)
            assertTrue(tool.isActive)
            assertTrue(client.calls.isEmpty())
        } finally {
            tool.cancelAndJoin()
        }
    }

    @Test
    fun targetRenameDuringPreparationStopsTheSameInvocation(): Unit = runBlocking {
        val controls = controls()
        val binding = saved(ShortcutAction.Scene(" Camera "))
        client.afterProgramRead = { client.emit(ObsEvent.SceneNameChanged(" Camera ", "New")) }
        controls.runner.run(origin, binding)
        assertEquals(ShortcutIssue.TARGET_CHANGED, controls.runner.state.value!!.issue)
        assertTrue(client.calls.isEmpty())
    }

    private suspend fun assertIssue(expected: ShortcutIssue, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertEquals(expected, (failure as ShortcutStopped).issue)
    }

    @Test
    fun aResolvedTargetCannotChangeTheSavedAction(): Unit = runBlocking {
        val controls = controls()
        val plan = controls.source.prepare(origin, saved(ShortcutAction.Mute("Mic", true)))
        listOf(
                ShortcutTarget.Mute("Other", true),
                ShortcutTarget.Mute("Mic", false),
                ShortcutTarget.Stream(true),
            )
            .forEach { target ->
                assertIssue(ShortcutIssue.BINDING_CHANGED) {
                    controls.source.write(plan.copy(target = target)) { error("Must not dispatch") }
                }
            }
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun allDirectActionsSendTheirExplicitTargetValues(): Unit = runBlocking {
        settings.setConfirmStopStream(false)
        settings.setConfirmStopRecord(false)
        val controls = controls()
        val actions =
            listOf(
                ShortcutAction.Mute("Mic", true),
                ShortcutAction.Mute("Mic", false),
                ShortcutAction.Stream(true),
                ShortcutAction.Stream(false),
                ShortcutAction.Recording(true),
                ShortcutAction.Recording(false),
            )
        actions.forEach { action ->
            val binding = saved(action)
            controls.runner.run(origin, binding)
            assertEquals(ShortcutOutcome.APPLIED, controls.runner.state.value!!.outcome)
            repository.delete(origin, binding)
        }
        assertEquals(
            listOf(
                "mute:Mic:true",
                "mute:Mic:false",
                "stream:true",
                "stream:false",
                "record:true",
                "record:false",
            ),
            client.calls,
        )
    }

    @Test
    fun freshOutputMatchAfterConsentNeverTogglesBack(): Unit = runBlocking {
        client.streamStatusResponse = client.streamStatusResponse.copy(active = true)
        val controls = controls()
        val binding = saved(ShortcutAction.Stream(false))
        val running = async { controls.runner.run(origin, binding) }
        val pending =
            withTimeout(5_000) {
                controls.runner.state.first { it?.phase == ShortcutPhase.CONFIRMATION }!!
            }
        client.streamStatusResponse = client.streamStatusResponse.copy(active = false)
        controls.runner.confirm(pending.id)
        running.await()
        assertEquals(ShortcutOutcome.ALREADY_MATCHED, controls.runner.state.value!!.outcome)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun unknownOutputStatesDoNotAuthorizeControl(): Unit = runBlocking {
        val controls = controls()
        val stream = saved(ShortcutAction.Stream(true))
        client.streamStatusResponse = client.streamStatusResponse.copy(reconnecting = true)
        assertIssue(ShortcutIssue.NOT_READY) { controls.source.prepare(origin, stream) }
        repository.delete(origin, stream)
        val record = saved(ShortcutAction.Recording(false))
        client.recordStatusResponse =
            client.recordStatusResponse.copy(active = false, paused = true)
        assertIssue(ShortcutIssue.UNAVAILABLE) { controls.source.prepare(origin, record) }
        assertTrue(client.calls.isEmpty())
    }
}
