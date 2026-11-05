package com.scenedeck.android.core.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetPreparationTest : PresetTestFixture() {
    private val client = PresetProbeClient()
    private val settings = SettingsRepository(SettingsRepositoryTest.newIsolatedStore())

    private fun preparation() = PresetPreparation(client, repository, settings) { 5_000 }

    @Test
    fun boundedPreparationPreservesSavedStepOrder(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        client.beforeRead = {
            val count = active.incrementAndGet()
            maximum.updateAndGet { old -> maxOf(old, count) }
            entered.send(Unit)
            try {
                gate.await()
            } finally {
                active.decrementAndGet()
            }
        }
        val ordered = List(12) { PresetStep.Mute("Input $it", true) }
        val preset = repository.save(origin, null, draft().copy(steps = ordered))
        val prepared = async { preparation().prepare(origin, preset) }
        withTimeout(5_000) { repeat(3) { entered.receive() } }
        assertEquals(3, active.get())
        assertTrue(entered.tryReceive().isFailure)
        gate.complete(Unit)
        val plan = prepared.await()
        assertEquals(ordered, plan.preset.steps)
        assertEquals(List(12) { PresetCheck.READY }, plan.checks)
        assertEquals(3, maximum.get())
        assertEquals(0, active.get())
        assertTrue(plan.canRun)
    }

    @Test
    fun failedStepDoesNotEraseOtherReadingsOrWaiveOutputSafety(): Unit = runBlocking {
        client.beforeRead = { if (it == "Broken") error("private payload") }
        val targets =
            listOf(
                PresetStep.Mute("Broken", true),
                PresetStep.Volume("Music", 0.25),
                PresetStep.Output(PresetOutput.STREAM, false),
                PresetStep.Output(PresetOutput.RECORDING, false),
            )
        val preset = repository.save(origin, null, draft().copy(steps = targets))
        val plan = preparation().prepare(origin, preset)
        assertEquals(
            listOf(
                PresetCheck.UNKNOWN,
                PresetCheck.CURRENTLY_MATCHES,
                PresetCheck.CURRENTLY_MATCHES,
                PresetCheck.CURRENTLY_MATCHES,
            ),
            plan.checks,
        )
        assertEquals(listOf(2, 3), plan.outputConfirmations)
        assertFalse(plan.canRun)
        assertFalse(plan.toString().contains("private payload"))
    }

    @Test
    fun editsAndDeletionDuringPreparationInvalidateTheWholePlan(): Unit = runBlocking {
        repeat(2) { attempt ->
            val gate = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            client.beforeRead = {
                entered.complete(Unit)
                gate.await()
            }
            val preset =
                repository.save(
                    origin,
                    null,
                    draft("Preset $attempt").copy(steps = listOf(PresetStep.Mute("Mic", true))),
                )
            val prepared = async { runCatching { preparation().prepare(origin, preset) } }
            withTimeout(5_000) { entered.await() }
            if (attempt == 0) repository.save(origin, preset, preset.draft().copy(name = "Changed"))
            else repository.delete(origin, preset)
            gate.complete(Unit)
            val failure = prepared.await().exceptionOrNull() as PresetPreparationException
            assertEquals(PresetPreparationFailure.CHANGED, failure.reason)
        }
    }

    @Test
    fun changedLocksDuringReadRejectThePreparedResult(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        client.beforeRead = {
            entered.complete(Unit)
            gate.await()
        }
        val preset =
            repository.save(
                origin,
                null,
                draft().copy(steps = listOf(PresetStep.Mute("Mic", true))),
            )
        val prepared = async { runCatching { preparation().prepare(origin, preset) } }
        withTimeout(5_000) { entered.await() }
        settings.setInputLocked("Mic", true)
        gate.complete(Unit)
        assertEquals(
            PresetPreparationFailure.CHANGED,
            (prepared.await().exceptionOrNull() as PresetPreparationException).reason,
        )
    }

    @Test
    fun cancelingPreparationCancelsItsReadChildren(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var active = false
        client.beforeRead = {
            active = true
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                active = false
            }
        }
        val preset =
            repository.save(
                origin,
                null,
                draft().copy(steps = listOf(PresetStep.Mute("Mic", true))),
            )
        val prepared = async { preparation().prepare(origin, preset) }
        withTimeout(5_000) { entered.await() }
        prepared.cancelAndJoin()
        assertTrue(prepared.isCancelled)
        assertFalse(active)
        assertTrue(client.sceneSwitchCalls.isEmpty())
    }
}
