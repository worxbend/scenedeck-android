package com.scenedeck.android.feature.shortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.WorkspaceSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal data class ShortcutsUiState(
    val workspace: ShortcutWorkspaceUi = ShortcutWorkspaceUi(),
    val edits: ShortcutEditUi = ShortcutEditUi(),
)

internal sealed interface ShortcutsAction {
    data class Open(val origin: WorkspaceSession, val binding: ShortcutBinding? = null) :
        ShortcutsAction

    data class Edit(val id: String, val change: ShortcutEdit) : ShortcutsAction

    data class Save(val id: String) : ShortcutsAction

    data class Remove(val origin: WorkspaceSession, val binding: ShortcutBinding) : ShortcutsAction

    data class Delete(val target: ShortcutDeletion) : ShortcutsAction

    data class Toggle(val origin: WorkspaceSession, val binding: ShortcutBinding) : ShortcutsAction

    data object DismissDelete : ShortcutsAction

    data object Retry : ShortcutsAction

    data object RefreshTargets : ShortcutsAction
}

@HiltViewModel
class ShortcutsViewModel
@Inject
constructor(private val repository: ShortcutRepository, private val workspace: ShortcutWorkspace) :
    ViewModel() {
    private val editor = ShortcutEditorController(repository, viewModelScope)
    internal val uiState =
        combine(workspace.state, editor.state, ::ShortcutsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShortcutsUiState())

    internal fun onAction(action: ShortcutsAction) {
        when (action) {
            is ShortcutsAction.Open -> editor.open(action.origin, action.binding)
            is ShortcutsAction.Edit -> editor.edit(action.id, action.change)
            is ShortcutsAction.Save -> editor.save(action.id)
            is ShortcutsAction.Remove -> editor.requestDelete(action.origin, action.binding)
            is ShortcutsAction.Delete -> editor.delete(action.target)
            is ShortcutsAction.Toggle -> editor.toggle(action.origin, action.binding)
            ShortcutsAction.DismissDelete -> editor.dismissDelete()
            ShortcutsAction.Retry -> {
                repository.retry()
                workspace.retryTargets()
            }
            ShortcutsAction.RefreshTargets -> workspace.retryTargets()
        }
    }
}
