package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetToolsConflictTest : PresetTestFixture() {
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = PresetToolsClient()
    private val settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
    private val clock = MonotonicClock { System.nanoTime() / 1_000_000 }

    @After fun stopTools(): Unit = runBlocking { background.coroutineContext.job.cancelAndJoin() }

    private fun tools(): Tools {
        val mixer = MixerRepository(client, settings, context, background, clock)
        val active = ActiveConnection().apply { confirm(begin(origin.key.profileId)) }
        val stats = StatsRepository(client, background, clock)
        val recording = RecordingToolsRepository(client, stats, active, clock, background)
        val source = SharedPresetExecutionSource(client, repository, settings, mixer, recording)
        return Tools(mixer, recording, source)
    }

    @Test
    fun pendingRecordingToolBlocksPreparedOutputBeforeAnyPresetWrite(): Unit = runBlocking {
        val tools = tools()
        val preset =
            repository.save(
                origin,
                null,
                draft().copy(steps = listOf(PresetStep.Output(PresetOutput.RECORDING, false))),
            )
        val plan = PresetPreparation(client, repository, settings, clock).prepare(origin, preset)
        val target =
            withTimeout(5_000) {
                tools.recording.state
                    .first { it.origin != null && it.record?.active == true }
                    .origin!!
            }
        val operation = launch { tools.recording.perform(target, RecordingAction.SPLIT) }
        withTimeout(5_000) { client.toolEntered.await() }
        try {
            assertEquals(
                PresetStopReason.BUSY,
                withTimeout(5_000) { tools.source.interruptions(plan).first() },
            )
            assertBlocked(plan, tools.source)
        } finally {
            operation.cancelAndJoin()
        }
    }

    @Test
    fun focusedOffProgramSeekBlocksPreparedPlaybackWithoutCancelingTheSeek(): Unit = runBlocking {
        val tools = tools()
        val preset =
            repository.save(
                origin,
                null,
                draft()
                    .copy(
                        steps = listOf(PresetStep.Media("Off program", PresetMediaState.PLAYING))
                    ),
            )
        val plan = PresetPreparation(client, repository, settings, clock).prepare(origin, preset)
        val observing = background.launch { tools.mixer.mediaStatus.collect {} }
        val lease = tools.mixer.watchMediaInput(origin, "Off program")
        val reading =
            withTimeout(5_000) {
                tools.mixer.mediaStatus
                    .first { it.readings["Off program"]?.status != null }
                    .readings
                    .getValue("Off program")
            }
        val operation = launch {
            tools.mixer.seekMedia(
                MediaSeekIntent(reading.target, durationMs = 60_000, cursorMs = 10_000)
            )
        }
        withTimeout(5_000) { client.toolEntered.await() }
        try {
            assertEquals(
                PresetStopReason.BUSY,
                withTimeout(5_000) { tools.source.interruptions(plan).first() },
            )
            assertBlocked(plan, tools.source)
            assertTrue(operation.isActive)
        } finally {
            operation.cancelAndJoin()
            lease.close()
            observing.cancelAndJoin()
        }
    }

    private suspend fun assertBlocked(plan: PresetPlan, source: PresetExecutionSource) {
        val runner = PresetRunner(source, FakePresetHistory(), clock, { 1_000 })
        runner.run(plan, plan.outputConfirmations.toSet())
        assertEquals(PresetStopReason.BUSY, runner.state.value!!.run.reason)
        assertTrue(runner.state.value!!.run.steps.all { it.result == PresetStepResult.NOT_RUN })
    }

    private data class Tools(
        val mixer: MixerRepository,
        val recording: RecordingToolsRepository,
        val source: PresetExecutionSource,
    )
}

private class PresetToolsClient : FakeObsClient() {
    val toolEntered = CompletableDeferred<Unit>()
    private val completion = CompletableDeferred<Unit>()

    init {
        setReady(
            capabilities =
                ObsCapabilities(
                    splitRecording = true,
                    mediaSeek = true,
                    controls = ObsControl.entries.toSet(),
                )
        )
        recordStatusResponse = recordStatusResponse.copy(active = true)
    }

    override suspend fun getMediaInputStatus(inputName: String) =
        MediaStatus(MediaStateKind.PAUSED, 60_000, 1_000)

    override suspend fun splitRecordFile() {
        toolEntered.complete(Unit)
        completion.await()
    }

    override suspend fun setMediaInputCursor(inputName: String, cursorMs: Long) {
        toolEntered.complete(Unit)
        completion.await()
    }
}
