package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.PresetHistoryStatus
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.PresetStepResult
import com.scenedeck.android.core.data.PresetStopReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class PresetsUserFlowTest {
    @Test
    fun createEditPrepareConfirmRunAndExportUseTheSameSavedDefinition() = runTest {
        withPresets {
            history.start(seed)
            wire.stream = true
            model.uiState.first { it.workspace.ready != null }
            model.onAction(PresetsAction.Editor.Open(origin))
            val editor = model.uiState.first { it.edits.editor != null }.edits.editor!!
            model.onAction(PresetsAction.Editor.Change(editor.id, PresetEdit.Name("End interview")))
            addStep(editor.id, PresetStepKind.PROGRAM, " Camera ")
            addStep(editor.id, PresetStepKind.MUTE, "Mic")
            addStep(editor.id, PresetStepKind.STREAM, "", false)
            model.onAction(PresetsAction.Editor.Save(editor.id))
            model.uiState.first { it.edits.editor == null && it.edits.message == "Preset saved." }
            val saved = repository.snapshot(origin).single()
            val review = prepareOnScreen(saved)
            assertFalse(review.canRun)
            model.onAction(PresetsAction.Review.Acknowledge(review.id, 2, true))
            model.onAction(PresetsAction.Review.Run(review.id))
            model.onAction(PresetsAction.Review.Run(review.id))
            val finished =
                model.uiState.first {
                    it.run?.run?.outcome == PresetRunOutcome.COMPLETED && !it.running
                }
            assertEquals(
                listOf("setCurrentProgramScene", "setInputMute", "stopStream"),
                wire.writes,
            )
            assertEquals(" Camera ", wire.program)
            val exported = history.exportSnapshot().observations.single().presetRuns.single().run
            assertEquals(finished.run!!.run, exported)
            assertEquals(saved.steps, exported.steps.map { it.target })
            assertEquals(saved.revision, exported.presetRevision)
        }
    }

    @Test
    fun leavingReviewPreventsRunAndNavigationDuringRunPreservesCancelableProgress() = runTest {
        withPresets {
            history.start(seed)
            val saved = save(listOf(PresetStep.Mute("Mic", true), PresetStep.Mute("Mic", false)))
            val first = prepareOnScreen(saved)
            model.onAction(PresetsAction.LeaveScreen)
            model.onAction(PresetsAction.Review.Run(first.id))
            assertTrue(wire.writes.isEmpty())
            val entered = CompletableDeferred<Unit>()
            wire.beforeWrite = {
                entered.complete(Unit)
                awaitCancellation()
            }
            val second = prepareOnScreen(saved)
            model.onAction(PresetsAction.Review.Run(second.id))
            entered.await()
            model.onAction(PresetsAction.LeaveScreen)
            runCurrent()
            assertTrue(launcher.busy.value)
            val id = launcher.state.value!!.run.id
            model.onAction(PresetsAction.CancelRun(id))
            val canceled =
                model.uiState.first {
                    it.run?.run?.outcome == PresetRunOutcome.CANCELLED && !it.running
                }
            assertEquals(
                listOf(PresetStepResult.UNKNOWN, PresetStepResult.NOT_RUN),
                canceled.run!!.run.steps.map { it.result },
            )
            assertEquals(
                canceled.run.run,
                history.exportSnapshot().observations.single().presetRuns.single().run,
            )
            assertEquals(1, wire.writes.size)
        }
    }

    @Test
    fun uncertainSecondWriteKeepsConfirmedFirstStepAndNeverRunsThirdOrRetries() = runTest {
        withPresets {
            history.start(seed)
            val saved =
                save(
                    listOf(
                        PresetStep.Mute("Mic", true),
                        PresetStep.Volume("Mic", 0.5),
                        PresetStep.Mute("Mic", false),
                    )
                )
            wire.beforeWrite = {
                if (it == "setInputVolume") error("private OBS comment and password")
            }
            val review = prepareOnScreen(saved)
            model.onAction(PresetsAction.Review.Run(review.id))
            val stopped =
                model.uiState
                    .first { it.run?.run?.outcome == PresetRunOutcome.STOPPED && !it.running }
                    .run!!
            assertEquals(
                listOf(
                    PresetStepResult.CONFIRMED,
                    PresetStepResult.UNKNOWN,
                    PresetStepResult.NOT_RUN,
                ),
                stopped.run.steps.map { it.result },
            )
            assertEquals(listOf("setInputMute", "setInputVolume"), wire.writes)
            assertEquals(
                stopped.run,
                history.exportSnapshot().observations.single().presetRuns.single().run,
            )
            assertFalse(stopped.toString().contains("private OBS"))
        }
    }

    private suspend fun PresetUiFixture.prepareOnScreen(saved: ActionPreset): PresetReview {
        model.uiState.first { it.workspace.ready?.presets?.contains(saved) == true }
        val previous = model.uiState.value.review.review?.id
        model.onAction(PresetsAction.Review.Open(origin, saved))
        return model.uiState
            .first {
                it.review.review?.phase == PresetReviewPhase.READY &&
                    it.review.review.id != previous
            }
            .review
            .review!!
    }

    @Test
    fun clearingHistoryDuringRunDoesNotRecreateItsRecord() = runTest {
        withPresets {
            history.start(seed)
            val saved = save(listOf(PresetStep.Mute("Mic", true), PresetStep.Mute("Mic", false)))
            wire.beforeWrite = { history.delete() }
            val review = prepareOnScreen(saved)
            model.onAction(PresetsAction.Review.Run(review.id))
            val finished =
                model.uiState
                    .first {
                        it.run != null &&
                            it.run.run.outcome != PresetRunOutcome.RUNNING &&
                            !it.running
                    }
                    .run!!
            assertEquals(PresetRunOutcome.COMPLETED, finished.run.outcome)
            assertEquals(PresetHistoryStatus.REMOVED, finished.history)
            assertTrue(history.exportSnapshot().observations.isEmpty())
            assertEquals(2, wire.writes.size)
        }
    }

    @Test
    fun failedHistoryCheckpointStopsBeforeObsAndShowsSafeStorageFailure() = runTest {
        withPresets {
            history.start(seed)
            val saved = save()
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_run BEFORE UPDATE ON history_preset_runs " +
                    "BEGIN SELECT RAISE(ABORT, 'private database failure'); END"
            )
            val review = prepareOnScreen(saved)
            model.onAction(PresetsAction.Review.Run(review.id))
            val failed =
                model.uiState
                    .first {
                        it.run != null &&
                            it.run.run.outcome != PresetRunOutcome.RUNNING &&
                            !it.running
                    }
                    .run!!
            assertEquals(PresetStopReason.HISTORY_FAILED, failed.run.reason)
            assertEquals(PresetHistoryStatus.FAILED, failed.history)
            assertFalse(failed.toString().contains("private database"))
            assertTrue(wire.writes.isEmpty())
        }
    }

    @Test
    fun disabledCaptureRunsDeliberatelyWithExplicitUnrecordedResult() = runTest {
        withPresets {
            history.start(seed)
            history.configure(false, 30)
            val review = prepareOnScreen(save())
            model.onAction(PresetsAction.Review.Run(review.id))
            val finished =
                model.uiState
                    .first {
                        it.run != null &&
                            it.run.run.outcome != PresetRunOutcome.RUNNING &&
                            !it.running
                    }
                    .run!!
            assertEquals(PresetRunOutcome.COMPLETED, finished.run.outcome)
            assertEquals(PresetHistoryStatus.NOT_RECORDED, finished.history)
            assertTrue(history.exportSnapshot().observations.single().presetRuns.isEmpty())
            assertEquals(1, wire.writes.size)
        }
    }

    private suspend fun PresetUiFixture.addStep(
        editorId: String,
        kind: PresetStepKind,
        target: String,
        enabled: Boolean = true,
    ) {
        model.onAction(PresetsAction.Editor.Change(editorId, PresetEdit.EditStep(null)))
        val draft =
            model.uiState.first { it.edits.editor?.stepDraft != null }.edits.editor!!.stepDraft!!
        model.onAction(
            PresetsAction.Editor.Change(
                editorId,
                PresetEdit.ChangeStep(draft.copy(kind = kind, target = target, enabled = enabled)),
            )
        )
        model.onAction(PresetsAction.Editor.Change(editorId, PresetEdit.ApplyStep))
        model.uiState.first { it.edits.editor?.stepDraft == null }
    }
}
