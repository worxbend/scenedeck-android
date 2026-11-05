package com.scenedeck.android.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetHistoryTest : HistoryTestFixture() {
    private val process = LocalProcessIdentity()
    private val link = HistoryBookmarkLink(SEED.id, 50)

    private fun history(owner: LocalProcessIdentity = process) =
        StoredPresetRunHistory(database, owner) { link }

    private fun run(id: String = "run") =
        PresetRun(
            id,
            "preset",
            "Interview",
            4,
            10_050,
            listOf(
                PresetRunStep(PresetStep.Mute("Mic", true), PresetStepResult.PENDING),
                PresetRunStep(PresetStep.Mute("Music", true), PresetStepResult.PENDING),
                PresetRunStep(PresetStep.Mute("Other", true), PresetStepResult.PENDING),
            ),
        )

    @Test
    fun lateResultUpdatesOnlyOriginalClosedObservationAndExportsExactPartialResult(): Unit =
        runBlocking {
            repository.start(SEED)
            val history = history()
            val initial = run()
            assertEquals(PresetHistoryStatus.SAVED, history.start(SEED.workspace, link, initial))
            val writing =
                initial.step(0, PresetStepResult.CONFIRMED).step(1, PresetStepResult.WRITING)
            history.save(link, writing)
            repository.finish(
                HistoryPosition(SEED.id, SEED.ownerId, 10_100, 100),
                HistoryEndReason.DISCONNECTED,
            )
            repository.start(SEED.copy(id = "replacement", workspace = WorkspaceKey(9, "Other")))
            val stopped =
                writing.finish(PresetRunOutcome.STOPPED, PresetStopReason.WORKSPACE_CHANGED, 10_200)
            assertEquals(PresetHistoryStatus.SAVED, history.save(link, stopped))
            val snapshot = repository.exportSnapshot()
            assertTrue(snapshot.observations.first().presetRuns.isEmpty())
            val original = snapshot.observations.last()
            assertEquals(10_100L, original.session.endedAtMs)
            assertEquals(HistoryPresetRun(50, stopped), original.presetRuns.single())
            val exported = snapshot.exportJson(20_000)
            val saved =
                Json.parseToJsonElement(exported)
                    .jsonObject["observations"]!!
                    .jsonArray
                    .last()
                    .jsonObject["presetRuns"]!!
                    .jsonArray
            assertEquals(1, saved.size)
            assertTrue(exported.contains("UNKNOWN"))
            assertFalse(exported.contains(process.id))
            assertFalse(exported.contains(SEED.ownerId))
            assertEquals(PresetHistoryStatus.SAVED, history.save(link, stopped))
            assertTrue(runCatching { history.save(link, writing) }.isFailure)
        }

    @Test
    fun clearAndRetentionNeverResurrectRunsButDisableAllowsAnExistingResult(): Unit = runBlocking {
        repository.start(SEED)
        val history = history()
        val initial = run()
        history.start(SEED.workspace, link, initial)
        repository.configure(false, 10)
        val terminal =
            initial.finish(PresetRunOutcome.CANCELLED, PresetStopReason.CANCELLED, 10_200)
        assertEquals(PresetHistoryStatus.SAVED, history.save(link, terminal))
        assertEquals(
            PresetHistoryStatus.NOT_RECORDED,
            history.start(SEED.workspace, link, run("disabled")),
        )
        repository.delete()
        assertEquals(PresetHistoryStatus.REMOVED, history.save(link, terminal))
        assertEquals(
            PresetHistoryStatus.REMOVED,
            history.start(SEED.workspace, link, run("deleted")),
        )
        assertTrue(repository.exportSnapshot().observations.isEmpty())
    }

    @Test
    fun retryWithinProcessPreservesRunningButNewProcessInterruptsWithoutResuming(): Unit =
        runBlocking {
            val sameProcess = HistoryRepository(database, process)
            sameProcess.start(SEED)
            val history = history()
            val initial = run()
            history.start(SEED.workspace, link, initial)
            val writing =
                initial.step(0, PresetStepResult.CONFIRMED).step(1, PresetStepResult.WRITING)
            history.save(link, writing)
            sameProcess.recover("retry-owner", HistoryEndReason.CAPTURE_INTERRUPTED)
            assertEquals(
                writing,
                sameProcess.exportSnapshot().observations.single().presetRuns.single().run,
            )
            database.close()
            openHistory()
            repository.recover("new-process")
            val interrupted =
                repository.exportSnapshot().observations.single().presetRuns.single().run
            assertEquals(PresetRunOutcome.INTERRUPTED, interrupted.outcome)
            assertEquals(PresetStopReason.PROCESS_ENDED, interrupted.reason)
            assertNull(interrupted.finishedAtMs)
            assertEquals(
                listOf(
                    PresetStepResult.CONFIRMED,
                    PresetStepResult.UNKNOWN,
                    PresetStepResult.NOT_RUN,
                ),
                interrupted.steps.map { it.result },
            )
            assertTrue(
                runCatching {
                    history()
                        .save(
                            link,
                            writing.finish(
                                PresetRunOutcome.CANCELLED,
                                PresetStopReason.CANCELLED,
                                11_000,
                            ),
                        )
                }
                    .isFailure
            )
        }

    @Test
    fun scopeOwnerBoundsAndDeletedParentsAreChecked(): Unit = runBlocking {
        repository.start(SEED)
        val history = history()
        assertTrue(runCatching { history.start(WorkspaceKey(99, "Other"), link, run()) }.isFailure)
        repeat(MAX_HISTORY_PRESET_RUNS) { index ->
            assertEquals(
                PresetHistoryStatus.SAVED,
                history.start(SEED.workspace, link, run("run-$index")),
            )
        }
        assertEquals(
            PresetHistoryStatus.LIMIT_REACHED,
            history.start(SEED.workspace, link, run("overflow")),
        )
        assertEquals(
            MAX_HISTORY_PRESET_RUNS,
            repository.exportSnapshot().observations.single().presetRuns.size,
        )
        assertTrue(
            runCatching {
                history(LocalProcessIdentity())
                    .save(link, run("run-0").step(0, PresetStepResult.WRITING))
            }
                .isFailure
        )
        repository.configure(true, 10)
        repeat(10) { repository.start(SEED.copy(id = "next-$it")) }
        assertEquals(
            PresetHistoryStatus.REMOVED,
            history.save(link, run("run-0").step(0, PresetStepResult.WRITING)),
        )
        assertTrue(database.historyPresetDao().list(SEED.id, 51).isEmpty())
    }

    @Test
    fun corruptPayloadFailsDetailAndExportAndCannotPartiallyRecover(): Unit = runBlocking {
        repository.start(SEED)
        history().start(SEED.workspace, link, run())
        database.openHelper.writableDatabase.execSQL(
            "UPDATE history_preset_runs SET payload = ?",
            arrayOf("{\"version\":99}"),
        )
        assertEquals(
            HistoryDetailState.Failed,
            repository.detail(SEED.id).first { it !is HistoryDetailState.Loading },
        )
        assertTrue(runCatching { repository.exportSnapshot() }.isFailure)
        assertTrue(runCatching { repository.recover("new-process") }.isFailure)
        assertNull(database.historySessionDao().get(SEED.id)!!.endReason)
    }
}
