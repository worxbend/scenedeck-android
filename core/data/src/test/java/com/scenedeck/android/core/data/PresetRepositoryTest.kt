package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.ConnectionProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRepositoryTest : PresetTestFixture() {
    @Test
    fun reopenPreservesTypedOrderExactTargetsAndWorkspaceIsolation(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft("  Interview  "))
        assertEquals("Interview", saved.name)
        assertEquals(steps, saved.steps)
        database.close()
        reopen()
        assertEquals(listOf(saved), repository.snapshot(origin))
        listOf(WorkspaceKey(8, "Studio"), WorkspaceKey(7, "Other")).forEach { key ->
            val other = origin.copy(key = key)
            context.select(other)
            assertTrue(repository.snapshot(other).isEmpty())
            repository.save(other, null, draft())
            assertTrue(runCatching { repository.delete(other, saved) }.isFailure)
            assertTrue(
                runCatching { repository.snapshot(origin) }.exceptionOrNull()
                    is WorkspaceChangedException
            )
        }
        context.select(origin)
        assertEquals(listOf(saved), repository.snapshot(origin))
    }

    @Test
    fun stableCreationIdMakesRetryIdempotentWithoutOverwritingLaterEdits(): Unit = runBlocking {
        val draft = draft()
        val saved = repository.save(origin, null, draft)
        assertEquals(saved, repository.save(origin, null, draft))
        val changed = repository.save(origin, saved, draft.copy(name = "Changed"))
        assertTrue(runCatching { repository.save(origin, null, draft) }.isFailure)
        assertEquals(listOf(changed), repository.snapshot(origin))
    }

    @Test
    fun revisionsRejectStaleSavesAndDeletesEvenWhenTextReturnsToOriginal(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val changed = repository.save(origin, saved, saved.draft().copy(steps = steps.reversed()))
        val reverted = repository.save(origin, changed, saved.draft())
        assertEquals(saved.steps, reverted.steps)
        assertEquals(2L, reverted.revision)
        assertTrue(runCatching { repository.delete(origin, saved) }.isFailure)
        assertTrue(runCatching { repository.save(origin, saved, saved.draft()) }.isFailure)
        repository.delete(origin, reverted)
        assertTrue(runCatching { repository.save(origin, reverted, reverted.draft()) }.isFailure)
        assertTrue(repository.snapshot(origin).isEmpty())
    }

    @Test
    fun concurrentEditorsAndCaseInsensitiveNamesHaveOneWinner(): Unit = runBlocking {
        val results =
            listOf("Interview", " interview ")
                .map { name ->
                    async(Dispatchers.Default) {
                        runCatching { repository.save(origin, null, draft(name)) }
                    }
                }
                .awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        val original = repository.snapshot(origin).single()
        val edits =
            listOf("One", "Two")
                .map { name ->
                    async(Dispatchers.Default) {
                        runCatching {
                            repository.save(origin, original, original.draft().copy(name = name))
                        }
                    }
                }
                .awaitAll()
        assertEquals(1, edits.count { it.isSuccess })
        assertEquals(1L, repository.snapshot(origin).single().revision)
    }

    @Test
    fun boundsArePerWorkspaceAndDeletionFreesSpace(): Unit = runBlocking {
        repeat(MAX_ACTION_PRESETS) { repository.save(origin, null, draft("Preset $it")) }
        val failure = runCatching {
            repository.save(origin, null, draft("One more"))
        }
            .exceptionOrNull()
        assertEquals(PresetEditFailure.LIMIT, (failure as PresetEditException).reason)
        val saved = repository.snapshot(origin).first()
        repository.delete(origin, saved)
        repository.save(origin, null, draft("Replacement"))
        assertEquals(MAX_ACTION_PRESETS, repository.snapshot(origin).size)
        val other = origin.copy(key = WorkspaceKey(8, "Studio"))
        context.select(other)
        assertEquals(0L, repository.save(other, null, draft()).revision)
    }

    @Test
    fun replacementDuringWriteRollsBackWithoutTouchingTheNewWorkspace(): Unit = runBlocking {
        duringWrite = { context.unavailable() }
        val result = runCatching { repository.save(origin, null, draft()) }
        assertTrue(result.exceptionOrNull() is WorkspaceChangedException)
        context.select(origin)
        assertTrue(repository.snapshot(origin).isEmpty())
    }

    @Test
    fun callerCancellationDuringWritePropagatesAndRollsBack(): Unit = runBlocking {
        val save = async {
            val caller = currentCoroutineContext().job
            duringWrite = { caller.cancel() }
            repository.save(origin, null, draft())
        }
        save.join()
        assertTrue(save.isCancelled)
        assertTrue(repository.snapshot(origin).isEmpty())
    }

    @Test
    fun profileRemovalPreservesLocalPresets(): Unit = runBlocking {
        database
            .connectionProfileDao()
            .insert(ConnectionProfileEntity(7, "Studio", "host", 4455, 0, 0))
        val saved = repository.save(origin, null, draft())
        database.connectionProfileDao().deleteById(7)
        assertEquals(listOf(saved), repository.snapshot(origin))
        assertFalse(repository.snapshot(origin).isEmpty())
    }
}
