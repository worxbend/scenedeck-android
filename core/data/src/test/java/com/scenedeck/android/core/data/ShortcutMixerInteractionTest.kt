package com.scenedeck.android.core.data

import com.scenedeck.android.core.obs.ObsRequestFailedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class ShortcutMixerInteractionTest : ShortcutExecutionTestFixture() {
    @Test
    fun obsoleteShortcutMuteFailureCannotRollBackANewerMixerAction(): Unit = runBlocking {
        val controls = controls()
        val observing = background.launch { controls.mixer.mixerState.collect {} }
        withTimeout(5_000) {
            controls.mixer.mixerState.first { it.inputs.any { input -> input.name == "Mic" } }
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        client.beforeMuteComplete = { muted ->
            if (muted) {
                entered.complete(Unit)
                release.await()
                throw ObsRequestFailedException("SetInputMute", "ResourceNotFound", "private")
            }
        }
        val binding = saved(ShortcutAction.Mute("Mic", true))
        val running = async { controls.runner.run(origin, binding) }
        try {
            withTimeout(5_000) { entered.await() }
            withTimeout(5_000) { controls.mixer.mixerState.first { it.inputs.single().muted } }
            controls.mixer.setInputMute(origin, "Mic", false)
            release.complete(Unit)
            withTimeout(5_000) { running.await() }
            assertEquals(ShortcutIssue.REJECTED, controls.runner.state.value!!.issue)
            val state =
                withTimeout(5_000) { controls.mixer.mixerState.first { !it.inputs.single().muted } }
            assertFalse(state.inputs.single().muted)
            assertEquals(listOf("mute:Mic:true", "mute:Mic:false"), client.calls)
        } finally {
            release.complete(Unit)
            running.cancelAndJoin()
            observing.cancelAndJoin()
        }
    }
}
