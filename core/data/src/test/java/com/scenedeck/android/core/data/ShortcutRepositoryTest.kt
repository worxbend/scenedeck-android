package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.ConnectionProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutRepositoryTest : ShortcutTestFixture() {
    @Test
    fun reopenPreservesEveryActionAndLiteralTargetWithoutActivatingAnything(): Unit = runBlocking {
        val actions =
            listOf(
                ShortcutAction.Scene(" Camera "),
                ShortcutAction.TakePreview,
                ShortcutAction.Mute(" Mic/Aux ", true),
                ShortcutAction.Mute("Mic/Aux", false),
                ShortcutAction.Stream(true),
                ShortcutAction.Stream(false),
                ShortcutAction.Recording(true),
                ShortcutAction.Recording(false),
            )
        val saved = actions.mapIndexed { index, action ->
            repository.save(
                origin,
                null,
                draft("F${index + 1}", index).copy(action = action, enabled = index % 2 == 0),
            )
        }
        database.close()
        reopen()
        assertEquals(saved, repository.snapshot(origin))
        assertEquals(actions, repository.snapshot(origin).map { it.action })
    }

    @Test
    fun profilesCollectionsAndSessionReplacementsKeepBindingsIsolated(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
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
    fun idempotentCreationAndNoOpSaveNeverOverwriteLaterEdits(): Unit = runBlocking {
        val draft = draft()
        val saved = repository.save(origin, null, draft)
        assertEquals(saved, repository.save(origin, null, draft))
        assertEquals(saved, repository.save(origin, saved, draft))
        val changed = repository.save(origin, saved, draft.copy(enabled = false))
        assertEquals(1L, changed.revision)
        assertTrue(runCatching { repository.save(origin, null, draft) }.isFailure)
        assertEquals(listOf(changed), repository.snapshot(origin))
    }

    @Test
    fun revisionsRejectStaleSaveDeleteAndResurrection(): Unit = runBlocking {
        val saved = repository.save(origin, null, draft())
        val changed = repository.save(origin, saved, saved.draft().copy(enabled = false))
        val reverted = repository.save(origin, changed, saved.draft())
        assertEquals(saved.draft(), reverted.draft())
        assertEquals(2L, reverted.revision)
        assertTrue(runCatching { repository.delete(origin, saved) }.isFailure)
        assertTrue(runCatching { repository.save(origin, saved, saved.draft()) }.isFailure)
        repository.delete(origin, reverted)
        assertTrue(runCatching { repository.save(origin, reverted, reverted.draft()) }.isFailure)
        assertTrue(repository.snapshot(origin).isEmpty())
    }

    @Test
    fun concurrentChordClaimsAndEditorsHaveOnlyOneWinner(): Unit = runBlocking {
        val attempts =
            List(2) { draft() }
                .map { draft ->
                    async(Dispatchers.Default) {
                        runCatching { repository.save(origin, null, draft) }
                    }
                }
                .awaitAll()
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(
            ShortcutEditFailure.CHORD_IN_USE,
            (attempts.first { it.isFailure }.exceptionOrNull() as ShortcutEditException).reason,
        )
        val saved = repository.snapshot(origin).single()
        val edits =
            listOf("F1", "F2")
                .map { key ->
                    async(Dispatchers.Default) {
                        runCatching {
                            repository.save(
                                origin,
                                saved,
                                saved.draft().copy(chord = ShortcutChord(key)),
                            )
                        }
                    }
                }
                .awaitAll()
        assertEquals(1, edits.count { it.isSuccess })
        assertEquals(1L, repository.snapshot(origin).single().revision)
    }

    @Test
    fun disabledChordsStayReservedAndModifiersDistinguishBindings(): Unit = runBlocking {
        val disabled = repository.save(origin, null, draft().copy(enabled = false))
        val failure = runCatching { repository.save(origin, null, draft()) }.exceptionOrNull()
        assertEquals(ShortcutEditFailure.CHORD_IN_USE, (failure as ShortcutEditException).reason)
        repository.save(origin, null, draft(modifiers = ShortcutChord.CTRL))
        repository.delete(origin, disabled)
        repository.save(origin, null, draft())
        assertEquals(2, repository.snapshot(origin).size)
    }

    @Test
    fun boundsArePerWorkspaceAndDeletionFreesCapacity(): Unit = runBlocking {
        repeat(MAX_SHORTCUT_BINDINGS) { index ->
            repository.save(origin, null, draft("F${index % 12 + 1}", index / 12))
        }
        val failure = runCatching { repository.save(origin, null, draft("NUM0")) }.exceptionOrNull()
        assertEquals(ShortcutEditFailure.LIMIT, (failure as ShortcutEditException).reason)
        repository.delete(origin, repository.snapshot(origin).first())
        repository.save(origin, null, draft("NUM0"))
        assertEquals(MAX_SHORTCUT_BINDINGS, repository.snapshot(origin).size)
        val other = origin.copy(key = WorkspaceKey(8, "Studio"))
        context.select(other)
        assertEquals(0L, repository.save(other, null, draft()).revision)
    }

    @Test
    fun workspaceReplacementDuringWriteRollsBack(): Unit = runBlocking {
        duringWrite = { context.unavailable() }
        assertTrue(
            runCatching { repository.save(origin, null, draft()) }.exceptionOrNull()
                is WorkspaceChangedException
        )
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
    fun removingProfileRetainsLocalBindings(): Unit = runBlocking {
        database
            .connectionProfileDao()
            .insert(ConnectionProfileEntity(7, "Studio", "host", 4455, 0, 0))
        val saved = repository.save(origin, null, draft())
        database.connectionProfileDao().deleteById(7)
        assertEquals(listOf(saved), repository.snapshot(origin))
    }
}
