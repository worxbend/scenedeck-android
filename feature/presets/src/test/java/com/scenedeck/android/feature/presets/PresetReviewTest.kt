package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.PresetCheck
import com.scenedeck.android.core.data.PresetOutput
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetSceneDestination
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class PresetReviewTest {
    @Test
    fun everyRequiredOutputConsentIsExplicitAndConsumedBeforeFirstWrite() = runTest {
        withPresets {
            history.start(seed)
            wire.stream = true
            wire.recording = true
            val preset =
                save(
                    listOf(
                        PresetStep.Output(PresetOutput.STREAM, false),
                        PresetStep.Output(PresetOutput.RECORDING, false),
                    )
                )
            review.prepare(origin, preset)
            val ready = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            assertEquals(listOf(0, 1), ready.plan!!.outputConfirmations)
            review.acknowledge("obsolete", 0, true)
            review.acknowledge(ready.id, 99, true)
            review.run(ready.id)
            assertTrue(wire.writes.isEmpty())
            review.acknowledge(ready.id, 0, true)
            assertFalse(review.state.value.review!!.canRun)
            review.acknowledge(ready.id, 1, true)
            assertTrue(review.state.value.review!!.canRun)
            review.run(ready.id)
            review.run(ready.id)
            assertNull(review.state.value.review)
            val result =
                launcher.state.first { it != null && it.run.outcome != PresetRunOutcome.RUNNING }!!
            launcher.busy.first { !it }
            assertEquals(result.toString(), PresetRunOutcome.COMPLETED, result.run.outcome)
            assertEquals(listOf("stopStream", "stopRecord"), wire.writes)
            assertEquals(
                result.run,
                history.exportSnapshot().observations.single().presetRuns.single().run,
            )
        }
    }

    @Test
    fun expiryClearsAllConsentAndOldRunCallbackCannotExecute() = runTest {
        withPresets {
            val preset = save(listOf(PresetStep.Output(PresetOutput.STREAM, false)))
            review.prepare(origin, preset)
            val ready = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            review.acknowledge(ready.id, 0, true)
            advanceTimeBy(60_000)
            runCurrent()
            val expired = review.state.value.review!!
            assertEquals(PresetReviewPhase.EXPIRED, expired.phase)
            assertTrue(expired.acknowledged.isEmpty())
            review.run(ready.id)
            assertTrue(wire.writes.isEmpty())
            assertNull(launcher.state.value)
        }
    }

    @Test
    fun lockOrSavedDefinitionChangeInvalidatesPreparedConsent() = runTest {
        withPresets {
            val preset = save()
            review.prepare(origin, preset)
            val first = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            settings.setInputLocked("Mic", true)
            review.state.first { it.review?.phase == PresetReviewPhase.CHANGED }
            review.run(first.id)
            settings.setInputLocked("Mic", false)
            review.prepare(origin, preset)
            val second = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            repository.save(origin, preset, preset.draft().copy(name = "New name"))
            review.state.first { it.review?.phase == PresetReviewPhase.CHANGED }
            review.run(second.id)
            assertTrue(wire.writes.isEmpty())
        }
    }

    @Test
    fun targetRenameInvalidatesOnlyMatchingSessionWithoutRetargeting() = runTest {
        withPresets {
            val preset = save()
            review.prepare(origin, preset)
            val ready = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            val rename = ObsEvent.InputNameChanged("Mic", "Renamed")
            wire.events.emit(ObsSessionEvent(ObsSessionToken(4), rename))
            runCurrent()
            assertEquals(PresetReviewPhase.READY, review.state.value.review!!.phase)
            wire.events.emit(ObsSessionEvent(origin.token, rename))
            review.state.first { it.review?.phase == PresetReviewPhase.CHANGED }
            review.run(ready.id)
            assertEquals(preset, repository.snapshot(origin).single())
            assertTrue(wire.writes.isEmpty())
        }
    }

    @Test
    fun closeAndWorkspaceReplacementDiscardReviewAndCannotUseItsOldCallback() = runTest {
        withPresets {
            val preset = save()
            review.prepare(origin, preset)
            val first = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            review.close()
            review.run(first.id)
            review.prepare(origin, preset)
            val second = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            workspaces.unavailable()
            review.run(second.id) // Synchronous guard before flow collectors get CPU time.
            assertFalse(review.state.value.review!!.canRun)
            assertTrue(wire.writes.isEmpty())
        }
    }

    @Test
    fun unavailableSceneIsShownAsMissingAndCannotRun() = runTest {
        withPresets {
            val preset =
                save(listOf(PresetStep.Scene("Removed scene", PresetSceneDestination.PROGRAM)))
            review.prepare(origin, preset)
            val ready = review.state.first { it.review?.phase == PresetReviewPhase.READY }.review!!
            assertEquals(listOf(PresetCheck.MISSING), ready.plan!!.checks)
            assertFalse(ready.canRun)
            review.run(ready.id)
            assertTrue(wire.writes.isEmpty())
        }
    }
}
