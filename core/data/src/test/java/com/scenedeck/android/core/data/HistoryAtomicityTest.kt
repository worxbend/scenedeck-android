package com.scenedeck.android.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryAtomicityTest : HistoryTestFixture() {
    @Test
    fun simultaneousRetriesPersistOneEvent(): Unit = runBlocking {
        repository.start(SEED)
        val results =
            List(8) { async(Dispatchers.Default) { repository.append(write(1)) } }.awaitAll()
        assertEquals(1, results.count { it == HistoryAppendResult.ADDED })
        assertEquals(7, results.count { it == HistoryAppendResult.ALREADY_SAVED })
        assertEquals(1, repository.exportSnapshot().observations.single().events.size)
    }

    @Test
    fun clearRacingAnOldStartCannotRestoreClearedHistory(): Unit = runBlocking {
        val start = async(Dispatchers.Default) { repository.start(SEED) }
        val clear = async(Dispatchers.Default) { repository.delete() }
        start.await()
        clear.await()
        assertTrue(repository.exportSnapshot().observations.isEmpty())
        assertEquals(HistoryAppendResult.UNAVAILABLE, repository.append(write(1)))
    }

    @Test
    fun failureAfterEventInsertRollsBackAndExplicitRetryRemainsIdempotent(): Unit = runBlocking {
        repository.start(SEED)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_history_update BEFORE UPDATE ON history_sessions " +
                "BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        assertTrue(runCatching { repository.append(write(1)) }.isFailure)
        assertTrue(database.historyEventDao().list(SEED.id, 501).isEmpty())
        assertEquals(0L, repository.exportSnapshot().observations.single().session.lastOrdinal)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_history_update")
        assertEquals(HistoryAppendResult.ADDED, repository.append(write(1)))
        assertEquals(HistoryAppendResult.ALREADY_SAVED, repository.append(write(1)))
    }
}
