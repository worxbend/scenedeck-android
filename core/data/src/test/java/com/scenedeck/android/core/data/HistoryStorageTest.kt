package com.scenedeck.android.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStorageTest : HistoryTestFixture() {
    @Test
    fun startsAreIdempotentAndCannotOverwriteIdentity(): Unit = runBlocking {
        assertTrue(repository.start(SEED))
        assertTrue(repository.start(SEED))
        assertTrue(
            runCatching { repository.start(SEED.copy(workspace = WorkspaceKey(8, "Studio"))) }
                .isFailure
        )
        repository.start(SEED.copy(id = "other", workspace = WorkspaceKey(8, "Studio")))
        val observations = repository.exportSnapshot().observations
        assertEquals(listOf(8L, 7L), observations.map { it.session.workspace.profileId })
        assertEquals(listOf("other", SEED.id), observations.map { it.session.id })
    }

    @Test
    fun replayIsDeduplicatedAndGapsRemainExplicit(): Unit = runBlocking {
        repository.start(SEED)
        assertEquals(HistoryAppendResult.ADDED, repository.append(write(1)))
        assertEquals(HistoryAppendResult.ALREADY_SAVED, repository.append(write(1)))
        assertTrue(
            runCatching {
                repository.append(write(1, payload = HistoryPayload.Program("Other", false)))
            }
                .isFailure
        )
        repository.append(write(4))
        assertEquals(HistoryAppendResult.OBSOLETE, repository.append(write(2)))
        val detail = repository.exportSnapshot().observations.single()
        assertEquals(listOf(1L, 4L), detail.events.map { it.ordinal })
        assertEquals(2L, detail.events.last().missingBefore)
        assertEquals(2L, detail.session.missingEvents)
    }

    @Test
    fun eventRetentionUsesOrdinalsEvenWhenCalendarMovesBackward(): Unit = runBlocking {
        repository.start(SEED)
        for (ordinal in 3L..MAX_HISTORY_EVENTS + 4L) repository.append(write(ordinal))
        val detail = repository.exportSnapshot().observations.single()
        assertEquals(MAX_HISTORY_EVENTS, detail.events.size)
        assertEquals(5L, detail.events.first().ordinal)
        assertEquals(504L, detail.events.last().ordinal)
        assertEquals(2L, detail.session.omittedEvents)
        assertEquals(2L, detail.session.missingEvents)
        assertEquals(HistoryAppendResult.OBSOLETE, repository.append(write(1)))
        assertEquals(HistoryAppendResult.OBSOLETE, repository.append(write(3)))
        assertEquals(detail, repository.exportSnapshot().observations.single())
    }

    @Test
    fun sessionRetentionUsesInsertionOrderAndCascadesEvents(): Unit = runBlocking {
        repository.configure(true, 10)
        repeat(12) { index ->
            val id = "session-$index"
            repository.start(SEED.copy(id = id, startedAtMs = 10_000L - index))
            repository.append(write(1, id))
        }
        val observations = repository.exportSnapshot().observations
        assertEquals(10, observations.size)
        assertEquals("session-11", observations.first().session.id)
        assertEquals("session-2", observations.last().session.id)
        assertTrue(database.historyEventDao().list("session-0", 501).isEmpty())
        assertEquals(HistoryAppendResult.UNAVAILABLE, repository.append(write(2, "session-0")))
    }

    @Test
    fun clearInvalidatesPendingStartsAndDoesNotResurrectDeletedParents(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        repository.delete()
        assertFalse(repository.start(SEED))
        assertEquals(HistoryAppendResult.UNAVAILABLE, repository.append(write(2)))
        assertTrue(database.historyEventDao().list(SEED.id, 501).isEmpty())
        val snapshot = repository.exportSnapshot()
        assertTrue(snapshot.observations.isEmpty())
        assertEquals(1L, snapshot.preferences.generation)
        assertTrue(
            repository.start(SEED.copy(id = "new", generation = snapshot.preferences.generation))
        )
    }

    @Test
    fun deletingOlderObservationKeepsCurrentCaptureAndInvalidatesOldQueuedStarts(): Unit =
        runBlocking {
            repository.start(SEED.copy(id = "older"))
            repository.append(write(1, "older"))
            repository.start(SEED)
            repository.delete("older")
            assertTrue(repository.start(SEED))
            assertEquals(HistoryAppendResult.ADDED, repository.append(write(1)))
            assertFalse(repository.start(SEED.copy(id = "older")))
            assertTrue(database.historyEventDao().list("older", 501).isEmpty())
            assertEquals(SEED.id, repository.exportSnapshot().observations.single().session.id)
        }

    @Test
    fun disabledCapturePreservesSavedHistoryAndReenableNeedsFreshGeneration(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        repository.configure(false, 30)
        assertFalse(repository.start(SEED.copy(id = "disabled")))
        assertEquals(HistoryAppendResult.UNAVAILABLE, repository.append(write(2)))
        val closed = repository.exportSnapshot().observations.single().session
        assertEquals(HistoryEndReason.CAPTURE_DISABLED, closed.endReason)
        assertEquals(null, closed.endedAtMs)
        repository.configure(true, 10)
        assertFalse(repository.start(SEED.copy(id = "old-generation")))
        val settings = repository.exportSnapshot().preferences
        assertEquals(2L, settings.generation)
        assertTrue(repository.start(SEED.copy(id = "enabled", generation = settings.generation)))
        assertEquals(2, repository.exportSnapshot().observations.size)
    }

    @Test
    fun invalidWritesAndSettingsRollbackWithoutChangingSavedData(): Unit = runBlocking {
        repository.start(SEED)
        repository.append(write(1))
        val before = repository.exportSnapshot()
        assertTrue(runCatching { repository.append(write(2).copy(elapsedMs = 0)) }.isFailure)
        assertTrue(runCatching { repository.append(write(2).copy(ownerId = "wrong")) }.isFailure)
        assertTrue(runCatching { repository.configure(false, 1) }.isFailure)
        assertEquals(before, repository.exportSnapshot())
    }
}
