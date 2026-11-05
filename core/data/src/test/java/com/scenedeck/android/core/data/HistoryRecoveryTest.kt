package com.scenedeck.android.core.data

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecoveryTest : HistoryTestFixture() {
    @Test
    fun reopeningClosesInterruptedObservationWithUnknownEndAndPreservesEvents(): Unit =
        runBlocking {
            repository.start(SEED)
            repository.append(
                write(
                    1,
                    payload =
                        HistoryPayload.Output(
                            HistoryOutput.STREAM,
                            HistoryOutputState.ACTIVE,
                            true,
                        ),
                )
            )
            database.close()
            openHistory()
            repository.recover("process-b")
            repository.start(SEED.copy(id = "fresh", ownerId = "process-b"))
            repository.recover("process-b")
            val observations = repository.exportSnapshot().observations
            assertEquals(null, observations.first().session.endReason)
            val interrupted = observations.last()
            assertEquals(HistoryEndReason.PROCESS_ENDED, interrupted.session.endReason)
            assertEquals(null, interrupted.session.endedAtMs)
            assertEquals(9_999L, interrupted.session.lastObservedAtMs)
            assertEquals(1, interrupted.events.size)
            assertEquals(HistoryAppendResult.UNAVAILABLE, repository.append(write(2)))
            assertFalse(repository.start(SEED))
        }

    @Test
    fun finishingIsBoundToOwnerAndCannotAppendToClosedObservation(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        val position = HistoryPosition(SEED.id, SEED.ownerId, 9_000, 200)
        assertTrue(
            runCatching {
                repository.finish(
                    position.copy(ownerId = "wrong"),
                    HistoryEndReason.DISCONNECTED,
                )
            }
                .isFailure
        )
        assertTrue(repository.finish(position, HistoryEndReason.WORKSPACE_CHANGED))
        assertFalse(
            repository.finish(position.copy(observedAtMs = 20_000), HistoryEndReason.DISCONNECTED)
        )
        assertFalse(repository.touch(position.copy(elapsedMs = 300)))
        assertEquals(HistoryAppendResult.UNAVAILABLE, repository.append(write(2)))
        val saved = repository.exportSnapshot().observations.single().session
        assertEquals(HistoryEndReason.WORKSPACE_CHANGED, saved.endReason)
        assertEquals(9_000L, saved.endedAtMs)
    }

    @Test
    fun heartbeatUpdatesObservationWithoutAddingEvents(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        val position = HistoryPosition(SEED.id, SEED.ownerId, 8_000, 300)
        assertTrue(repository.touch(position))
        assertTrue(runCatching { repository.touch(position.copy(elapsedMs = 299)) }.isFailure)
        val saved = repository.exportSnapshot().observations.single()
        assertEquals(300L, saved.session.elapsedMs)
        assertEquals(8_000L, saved.session.lastObservedAtMs)
        assertEquals(1, saved.events.size)
    }

    @Test
    fun corruptPayloadIsFailedNotEmptyAndRetryCanReadRepairedStorage(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE history_events SET payload = '{invalid}'"
        )
        withTimeout(5_000) {
            repository.detail(SEED.id).test {
                assertEquals(HistoryDetailState.Loading, awaitItem())
                assertEquals(HistoryDetailState.Failed, awaitItem())
                assertTrue(runCatching { repository.exportSnapshot() }.isFailure)
                repository.delete()
                repository.retry()
                assertEquals(HistoryDetailState.Loading, awaitItem())
                assertEquals(HistoryDetailState.Missing, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun closedStorageAndReadCancellationAreNotReportedAsEmptySuccess(): Unit = runBlocking {
        database.close()
        assertTrue(runCatching { repository.start(SEED) }.isFailure)
        assertEquals(
            HistoryState.Failed,
            withTimeout(5_000) { repository.state.first { it !is HistoryState.Loading } },
        )
        val cancellation = runCatching {
            repository.state.collect { throw CancellationException("cancel caller") }
        }
            .exceptionOrNull()
        assertTrue(cancellation is CancellationException)
    }

    @Test
    fun emptyAndDeletedDetailHaveDistinctReadableStates(): Unit = runBlocking {
        val empty = repository.state.first { it is HistoryState.Ready } as HistoryState.Ready
        assertEquals(HistoryPreferences(), empty.preferences)
        assertTrue(empty.sessions.isEmpty())
        repository.start(SEED)
        val ready =
            repository.detail(SEED.id).first { it !is HistoryDetailState.Loading }
                as HistoryDetailState.Ready
        assertTrue(ready.detail.events.isEmpty())
        repository.delete(SEED.id)
        assertEquals(
            HistoryDetailState.Missing,
            repository.detail(SEED.id).first { it !is HistoryDetailState.Loading },
        )
    }
}
