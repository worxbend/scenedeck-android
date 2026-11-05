package com.scenedeck.android.feature.shortcuts

import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutEditException
import com.scenedeck.android.core.data.ShortcutEditFailure
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.WorkspaceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ShortcutEditorController(
    private val repository: ShortcutRepository,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow(ShortcutEditUi())
    val state = current.asStateFlow()

    fun open(origin: WorkspaceSession, binding: ShortcutBinding?) {
        if (!available(origin)) return
        current.value = ShortcutEditUi(editor = ShortcutEditorState(origin, binding))
    }

    fun edit(id: String, action: ShortcutEdit) {
        val editor = current.value.editor?.takeIf { it.id == id && !current.value.busy } ?: return
        val closing =
            action in setOf(ShortcutEdit.Close, ShortcutEdit.Discard, ShortcutEdit.KeepEditing)
        if (!closing && !repository.workspaces.isCurrent(editor.origin)) return
        current.value = current.value.copy(editor = editor.reduce(action), message = null)
    }

    fun save(id: String) {
        val editor = current.value.editor?.takeIf { it.id == id && !current.value.busy } ?: return
        if (!editor.valid) return
        write({ ShortcutEditUi(message = "Shortcut saved.") }) {
            repository.save(editor.origin, editor.original, editor.value())
        }
    }

    fun requestDelete(origin: WorkspaceSession, binding: ShortcutBinding) {
        if (available(origin))
            current.value = ShortcutEditUi(deletion = ShortcutDeletion(origin, binding))
    }

    fun dismissDelete() {
        if (!current.value.busy) current.value = current.value.copy(deletion = null, message = null)
    }

    fun delete(target: ShortcutDeletion) {
        if (current.value.busy || current.value.deletion != target) return
        write({ ShortcutEditUi(message = "Shortcut deleted.") }) {
            repository.delete(target.origin, target.binding)
        }
    }

    fun toggle(origin: WorkspaceSession, binding: ShortcutBinding) {
        if (!available(origin)) return
        write({
            ShortcutEditUi(
                message = if (binding.enabled) "Shortcut disabled." else "Shortcut enabled."
            )
        }) {
            repository.save(origin, binding, binding.draft().copy(enabled = !binding.enabled))
        }
    }

    private fun available(origin: WorkspaceSession): Boolean {
        val idle =
            !current.value.busy && current.value.editor == null && current.value.deletion == null
        return idle && repository.workspaces.isCurrent(origin)
    }

    private fun write(success: () -> ShortcutEditUi, operation: suspend () -> Unit) {
        current.value = current.value.copy(busy = true, message = null)
        scope.launch {
            try {
                val result = coroutineResult { operation() }
                current.value =
                    if (result.isSuccess) success()
                    else
                        current.value.copy(
                            message =
                                result.exceptionOrNull().editMessage(current.value.editor != null)
                        )
            } finally {
                current.value = current.value.copy(busy = false)
            }
        }
    }
}

private fun ShortcutEditorState.reduce(edit: ShortcutEdit): ShortcutEditorState? =
    when (edit) {
        is ShortcutEdit.Kind ->
            copy(
                kind = edit.value,
                name = name.takeIf { kind.targetType == edit.value.targetType }.orEmpty(),
            )
        is ShortcutEdit.Name -> copy(name = edit.value)
        is ShortcutEdit.Chord -> copy(draft = draft.copy(chord = edit.value))
        is ShortcutEdit.Enabled -> copy(draft = draft.copy(enabled = edit.value))
        ShortcutEdit.Close -> if (dirty) copy(discard = true) else null
        ShortcutEdit.Discard -> null
        ShortcutEdit.KeepEditing -> copy(discard = false)
    }

private fun Throwable?.editMessage(hasDraft: Boolean): String {
    val retained = if (hasDraft) " Your draft is still here." else ""
    return when ((this as? ShortcutEditException)?.reason) {
        ShortcutEditFailure.CHORD_IN_USE ->
            "That key is already assigned in this workspace, including disabled shortcuts. Choose " +
                "another key or delete that binding."
        ShortcutEditFailure.CHANGED ->
            "The saved shortcut changed. Close and reopen to use the latest version.$retained"
        ShortcutEditFailure.LIMIT ->
            "This workspace has 32 shortcuts. Delete one before adding another."
        ShortcutEditFailure.INVALID -> "Choose a supported key and valid action target.$retained"
        else ->
            "Couldn't save the change. Check the connection and workspace, then try again.$retained"
    }
}
