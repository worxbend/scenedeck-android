package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsSessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetExecutionSourceTest : PresetTestFixture() {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun stopBackground(): Unit = runBlocking {
        backgroundScope.coroutineContext.job.cancelAndJoin()
    }

    private val client = ExecutingPresetClient()
    private val settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())
    private val history = FakePresetHistory()
    private val clock = MonotonicClock { 0 }

    private fun runner(): PresetRunner {
        val mixer = MixerRepository(client, settings, context, backgroundScope, clock)
        val active = ActiveConnection().apply { confirm(begin(origin.key.profileId)) }
        val stats = StatsRepository(client, backgroundScope, clock)
        val recording = RecordingToolsRepository(client, stats, active, clock, backgroundScope)
        return PresetRunner(
            SharedPresetExecutionSource(client, repository, settings, mixer, recording),
            history,
            clock,
        ) {
            20_000
        }
    }

    @Test
    fun savedPlanUsesTypedTargetWritesAndFreshReadsAcrossRepeatedControls(): Unit = runBlocking {
        val targets =
            listOf(
                PresetStep.Scene("Camera", PresetSceneDestination.PROGRAM),
                PresetStep.Scene("Camera", PresetSceneDestination.PREVIEW),
                PresetStep.Mute("Mic", true),
                PresetStep.Volume("Music", 0.25),
                PresetStep.Media("Clip", PresetMediaState.PLAYING),
                PresetStep.Output(PresetOutput.STREAM, true),
                PresetStep.Output(PresetOutput.RECORDING, true),
                PresetStep.Media("Clip", PresetMediaState.PAUSED),
                PresetStep.Output(PresetOutput.STREAM, false),
                PresetStep.Output(PresetOutput.RECORDING, false),
                PresetStep.Mute("Mic", false),
                PresetStep.Volume("Music", 1.0),
            )
        val preset = repository.save(origin, null, draft().copy(steps = targets))
        val plan = PresetPreparation(client, repository, settings, clock).prepare(origin, preset)
        val runner = runner()
        runner.run(plan, plan.outputConfirmations.toSet())
        assertEquals(PresetRunOutcome.COMPLETED, runner.state.value!!.run.outcome)
        assertTrue(runner.state.value!!.run.steps.all { it.result == PresetStepResult.CONFIRMED })
        assertEquals(
            listOf(
                "program:Camera",
                "preview:Camera",
                "mute:true",
                "volume:0.25",
                "media:PLAY",
                "stream:true",
                "record:true",
                "media:PAUSE",
                "stream:false",
                "record:false",
                "mute:false",
                "volume:1.0",
            ),
            client.calls,
        )
    }

    @Test
    fun policyChangeDuringWriteJournalIsRecheckedBeforeAnyControlWrite(): Unit = runBlocking {
        val preset =
            repository.save(
                origin,
                null,
                draft().copy(steps = listOf(PresetStep.Mute("Mic", true))),
            )
        val plan = PresetPreparation(client, repository, settings, clock).prepare(origin, preset)
        history.beforeSave = { run ->
            if (run.steps.single().result == PresetStepResult.WRITING) {
                settings.setInputLocked("Mic", true)
                settings.settings.first { "Mic" in it.lockedInputs }
            }
        }
        val runner = runner()
        runner.run(plan)
        assertEquals(PresetStopReason.POLICY_CHANGED, runner.state.value!!.run.reason)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun replacementAndChangedDefinitionRejectThePreparedTargets(): Unit = runBlocking {
        val preset =
            repository.save(
                origin,
                null,
                draft().copy(steps = listOf(PresetStep.Mute("Mic", true))),
            )
        val plan = PresetPreparation(client, repository, settings, clock).prepare(origin, preset)
        repository.save(origin, preset, preset.draft().copy(name = "Changed"))
        val runner = runner()
        runner.run(plan)
        assertEquals(PresetStopReason.PRESET_CHANGED, runner.state.value!!.run.reason)
        context.select(origin.copy(token = ObsSessionToken(5)))
        runner.run(plan)
        assertEquals(PresetStopReason.WORKSPACE_CHANGED, runner.state.value!!.run.reason)
        assertTrue(client.calls.isEmpty())
    }
}
