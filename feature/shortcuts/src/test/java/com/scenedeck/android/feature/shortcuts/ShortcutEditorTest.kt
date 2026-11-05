package com.scenedeck.android.feature.shortcuts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutDraft
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.database.SceneDeckDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ShortcutEditorTest {
    @Test
    fun newDraftHasNoDefaultKeyAndConflictRetainsStableIdentityUntilCorrected() = runTest {
        withEditor {
            save(enabled = false)
            editor.open(origin, null)
            val draft = editor.state.value.editor!!
            assertNull(draft.draft.chord)
            edit(ShortcutEdit.Chord(ShortcutChord("F8")))
            edit(ShortcutEdit.Name(" Camera "))
            editor.save(draft.id)
            val failed = editor.state.first { !it.busy && it.message != null }
            assertTrue(failed.message!!.contains("already assigned"))
            assertEquals(draft.draft.id, failed.editor!!.draft.id)
            edit(ShortcutEdit.Chord(ShortcutChord("F9", ShortcutChord.CTRL)))
            editor.save(draft.id)
            editor.save(draft.id)
            editor.state.first { !it.busy && it.editor == null }
            val saved = repository.snapshot(origin).first { it.id == draft.draft.id }
            assertEquals(ShortcutAction.Scene(" Camera "), saved.action)
            assertEquals(0L, saved.revision)
        }
    }

    @Test
    fun toggleAndDeleteCompareOriginalAndDisabledBindingStillReservesKey() = runTest {
        withEditor {
            val original = save()
            editor.toggle(origin, original)
            editor.state.first { !it.busy && it.message != null }
            val disabled = repository.snapshot(origin).single()
            assertFalse(disabled.enabled)
            editor.requestDelete(origin, original)
            editor.delete(editor.state.value.deletion!!)
            val failed = editor.state.first { !it.busy && it.message != null }
            assertTrue(failed.message!!.contains("changed"))
            assertFalse(failed.message.contains("draft"))
            assertEquals(disabled, repository.snapshot(origin).single())
            editor.dismissDelete()
            editor.requestDelete(origin, disabled)
            val target = editor.state.value.deletion!!
            editor.delete(target)
            editor.delete(target)
            editor.state.first { !it.busy && it.deletion == null }
            assertTrue(repository.snapshot(origin).isEmpty())
            save()
        }
    }

    @Test
    fun changedScopeAndConcurrentRevisionRetainDraftWithoutOverwriting() = runTest {
        withEditor {
            val original = save()
            editor.open(origin, original)
            val id = editor.state.value.editor!!.id
            edit(ShortcutEdit.Name("Unsaved"))
            val next = origin.copy(key = WorkspaceKey(8, "Other"))
            workspaces.select(next)
            editor.save(id)
            editor.state.first { !it.busy && it.message != null }
            assertEquals(origin, editor.state.value.editor!!.origin)
            assertEquals("Unsaved", editor.state.value.editor!!.name)
            assertTrue(repository.snapshot(next).isEmpty())
            workspaces.select(origin)
            val updated = repository.save(origin, original, original.draft().copy(enabled = false))
            editor.save(id)
            val failed = editor.state.first { !it.busy && it.message != null }
            assertTrue(failed.message!!.contains("changed"))
            assertEquals(updated, repository.snapshot(origin).single())
        }
    }

    @Test
    fun obsoleteCaptureAndDeleteCallbacksCannotAlterAnotherEditor() = runTest {
        withEditor {
            val binding = save()
            editor.requestDelete(origin, binding)
            val delete = editor.state.value.deletion!!
            editor.dismissDelete()
            editor.open(origin, binding)
            val old = editor.state.value.editor!!.id
            edit(ShortcutEdit.Name("Draft"))
            edit(ShortcutEdit.Close)
            assertTrue(editor.state.value.editor!!.discard)
            edit(ShortcutEdit.KeepEditing)
            assertFalse(editor.state.value.editor!!.discard)
            edit(ShortcutEdit.Discard)
            editor.open(origin, binding)
            editor.edit(old, ShortcutEdit.Chord(ShortcutChord("F1")))
            editor.delete(delete)
            assertEquals(binding.draft(), editor.state.value.editor!!.value())
            assertEquals(binding, repository.snapshot(origin).single())
        }
    }

    @Test
    fun longLiteralTargetsAreInvalidInsteadOfSilentlyTruncated() = runTest {
        withEditor {
            editor.open(origin, null)
            val name = "x".repeat(1_025)
            edit(ShortcutEdit.Name(name))
            edit(ShortcutEdit.Chord(ShortcutChord("A")))
            val draft = editor.state.value.editor!!
            assertEquals(name, draft.name)
            assertFalse(draft.valid)
            editor.save(draft.id)
            assertFalse(editor.state.value.busy)
            assertTrue(repository.snapshot(origin).isEmpty())
        }
    }

    private suspend fun TestScope.withEditor(block: suspend Fixture.() -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database =
            Room.inMemoryDatabaseBuilder(context, SceneDeckDatabase::class.java)
                .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
                .allowMainThreadQueries()
                .build()
        try {
            block(Fixture(this, database))
        } finally {
            database.close()
        }
    }

    private class Fixture(scope: TestScope, database: SceneDeckDatabase) {
        val origin = TEST_WORKSPACE
        val workspaces = MutableWorkspaceContext()
        val repository = ShortcutRepository(database, workspaces)
        val editor = ShortcutEditorController(repository, scope.backgroundScope)

        fun edit(action: ShortcutEdit) = editor.edit(editor.state.value.editor!!.id, action)

        suspend fun save(enabled: Boolean = true) =
            repository.save(
                origin,
                null,
                ShortcutDraft(
                    chord = ShortcutChord("F8"),
                    action = ShortcutAction.Scene("Camera"),
                    enabled = enabled,
                ),
            )
    }
}
