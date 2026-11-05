package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.PresetSceneDestination
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.WorkspaceKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class PresetEditorTest {
    @Test
    fun draftReordersDuplicatesWithoutChangingLiteralNamesAndSavesOnlyOnce() = runTest {
        withPresets {
            val original =
                save(
                    listOf(
                        PresetStep.Scene(" Camera ", PresetSceneDestination.PROGRAM),
                        PresetStep.Mute("Mic", true),
                    )
                )
            editor.open(origin, original)
            val draft = editor.state.value.editor!!
            editor.edit(draft.id, PresetEdit.Name("Interview edited"))
            editor.edit(draft.id, PresetEdit.Move(draft.steps[1].id, -1))
            editor.edit(draft.id, PresetEdit.EditStep(null))
            val editing = editor.state.value.editor!!.stepDraft!!
            editor.edit(
                draft.id,
                PresetEdit.ChangeStep(
                    editing.copy(kind = PresetStepKind.MUTE, target = "Mic", enabled = false)
                ),
            )
            editor.edit(draft.id, PresetEdit.ApplyStep)
            editor.save(draft.id)
            editor.save(draft.id)
            editor.state.first { !it.busy && it.editor == null }
            val saved = repository.snapshot(origin).single()
            assertEquals(original.id, saved.id)
            assertEquals(1L, saved.revision)
            assertEquals("Interview edited", saved.name)
            assertEquals(
                listOf(original.steps[1], original.steps[0], PresetStep.Mute("Mic", false)),
                saved.steps,
            )
            assertTrue(wire.writes.isEmpty())
        }
    }

    @Test
    fun duplicateNameFailureKeepsStableDraftAndCanBeCorrected() = runTest {
        withPresets {
            save()
            editor.open(origin, null)
            val first = editor.state.value.editor!!
            editor.edit(first.id, PresetEdit.Name("Interview"))
            editor.edit(first.id, PresetEdit.EditStep(null))
            val step = editor.state.value.editor!!.stepDraft!!
            editor.edit(first.id, PresetEdit.ChangeStep(step.copy(target = " Camera ")))
            editor.edit(first.id, PresetEdit.ApplyStep)
            editor.save(first.id)
            val failed = editor.state.first { !it.busy && it.message != null }
            assertEquals(first.draft.id, failed.editor!!.draft.id)
            assertTrue(failed.message!!.contains("already used"))
            editor.edit(first.id, PresetEdit.Name("Second"))
            editor.save(first.id)
            editor.state.first { !it.busy && it.editor == null }
            assertEquals(
                first.draft.id,
                repository.snapshot(origin).first { it.name == "Second" }.id,
            )
        }
    }

    @Test
    fun changedWorkspaceKeepsDraftAndCannotWriteToNewScope() = runTest {
        withPresets {
            val original = save()
            editor.open(origin, original)
            val id = editor.state.value.editor!!.id
            editor.edit(id, PresetEdit.Name("Unsaved work"))
            val next = origin.copy(key = WorkspaceKey(8, "Studio"))
            workspaces.select(next)
            editor.save(id)
            val failed = editor.state.first { !it.busy && it.message != null }
            assertEquals(origin, failed.editor!!.origin)
            assertEquals("Unsaved work", failed.editor.draft.name)
            assertTrue(repository.snapshot(next).isEmpty())
            workspaces.select(origin)
            assertEquals(original, repository.snapshot(origin).single())
        }
    }

    @Test
    fun obsoleteCallbacksDoNotReplaceDraftAndDiscardRequiresItsCurrentIdentity() = runTest {
        withPresets {
            val original = save()
            editor.open(origin, original)
            val id = editor.state.value.editor!!.id
            editor.edit(id, PresetEdit.Name("Changed"))
            editor.edit("obsolete", PresetEdit.Discard)
            editor.requestDelete(origin, original)
            editor.dismissDelete()
            assertEquals("Changed", editor.state.value.editor!!.draft.name)
            editor.edit(id, PresetEdit.Close)
            assertTrue(editor.state.value.editor!!.discard)
            editor.edit(id, PresetEdit.KeepEditing)
            assertFalse(editor.state.value.editor!!.discard)
            editor.edit(id, PresetEdit.Discard)
            editor.open(origin, null)
            editor.edit(id, PresetEdit.Name("Stale name"))
            assertEquals("", editor.state.value.editor!!.draft.name)
            assertEquals(original, repository.snapshot(origin).single())
        }
    }

    @Test
    fun staleSaveAndDeleteCannotOverwriteANewerDefinition() = runTest {
        withPresets {
            val original = save()
            editor.open(origin, original)
            val id = editor.state.value.editor!!.id
            val updated =
                repository.save(origin, original, original.draft().copy(name = "Updated elsewhere"))
            editor.edit(id, PresetEdit.Name("My draft"))
            editor.save(id)
            val failed = editor.state.first { !it.busy && it.message != null }
            assertTrue(failed.message!!.contains("saved preset changed"))
            assertEquals("My draft", failed.editor!!.draft.name)
            editor.edit(id, PresetEdit.Discard)
            editor.requestDelete(origin, original)
            editor.delete(editor.state.value.deletion!!)
            editor.state.first { !it.busy && it.message != null }
            assertEquals(updated, repository.snapshot(origin).single())
        }
    }

    @Test
    fun stepFormRejectsNonFiniteAndOutOfRangeVolumesAndPreservesExactTargets() {
        val draft = PresetStepEditor(kind = PresetStepKind.VOLUME, target = " Music ")
        listOf("NaN", "Infinity", "-1", "100.1", "").forEach {
            assertNull(draft.copy(volumePercent = it).step())
        }
        assertEquals(PresetStep.Volume(" Music ", 0.125), draft.copy(volumePercent = "12,5").step())
        assertEquals(" Music ", draft.changeKind(PresetStepKind.MUTE).target)
        assertEquals("", draft.changeKind(PresetStepKind.PROGRAM).target)
    }
}
