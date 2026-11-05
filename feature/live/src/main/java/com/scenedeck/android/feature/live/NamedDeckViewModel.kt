package com.scenedeck.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DeckEditException
import com.scenedeck.android.core.data.DeckEditFailure
import com.scenedeck.android.core.data.DeckImportPreview
import com.scenedeck.android.core.data.DeckSelection
import com.scenedeck.android.core.data.NamedDeck
import com.scenedeck.android.core.data.NamedDeckRepository
import com.scenedeck.android.core.data.NamedDeckState
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.applyImport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface DeckEditorPage {
    data object Browse : DeckEditorPage

    data class Name(val target: NamedDeck?, val draft: String) : DeckEditorPage

    data class Members(val target: NamedDeck, val draft: List<String>) : DeckEditorPage

    data class Delete(val target: NamedDeck) : DeckEditorPage

    data class Import(val preview: DeckImportPreview) : DeckEditorPage
}

internal data class DeckManagerState(
    val origin: WorkspaceSession,
    val page: DeckEditorPage = DeckEditorPage.Browse,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
@Suppress("TooManyFunctions") // Focused deck editor intents, separate from live OBS controls.
class NamedDeckViewModel
@Inject
constructor(private val repository: NamedDeckRepository, profiles: ProfileRepository) :
    ViewModel() {
    val connections =
        profiles.profiles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    val decks =
        repository.state.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            NamedDeckState.Unavailable(repository.workspaces.state.value),
        )
    private val _manager = MutableStateFlow<DeckManagerState?>(null)
    internal val manager = _manager.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    internal val transfer = NamedDeckFiles(repository, viewModelScope, ::showImport, ::message)

    init {
        viewModelScope.launch {
            repository.workspaces.state.collect {
                val editor = _manager.value
                if (editor != null && !repository.workspaces.isCurrent(editor.origin))
                    _manager.value = null
            }
        }
    }

    fun retry() = repository.retry()

    fun select(origin: WorkspaceSession, selection: DeckSelection) =
        perform(origin) { repository.select(origin, selection) }

    fun reorder(origin: WorkspaceSession, target: NamedDeck, names: List<String>) =
        perform(origin) { repository.replaceMembers(origin, target, names) }

    fun open(origin: WorkspaceSession) {
        if (repository.workspaces.isCurrent(origin) && !_busy.value)
            _manager.value = DeckManagerState(origin)
    }

    internal fun show(page: DeckEditorPage) {
        if (!_busy.value) _manager.value = _manager.value?.copy(page = page, error = null)
    }

    fun close() {
        if (!_busy.value) _manager.value = null
    }

    internal fun save() {
        val editor = _manager.value ?: return
        perform(editor.origin, returnToBrowse = true) {
            when (val page = editor.page) {
                DeckEditorPage.Browse -> Unit
                is DeckEditorPage.Name -> {
                    if (page.target == null) repository.create(editor.origin, page.draft)
                    else repository.rename(editor.origin, page.target, page.draft)
                }
                is DeckEditorPage.Members ->
                    repository.replaceMembers(editor.origin, page.target, page.draft)
                is DeckEditorPage.Delete -> repository.delete(editor.origin, page.target)
                is DeckEditorPage.Import -> repository.applyImport(page.preview)
            }
        }
    }

    private fun showImport(preview: DeckImportPreview) {
        if (!repository.workspaces.isCurrent(preview.origin)) {
            message("Workspace changed. Import decks again in the intended workspace.")
        } else if (_busy.value) {
            message("Finish the current deck edit, then import again.")
        } else _manager.value = DeckManagerState(preview.origin, DeckEditorPage.Import(preview))
    }

    private fun message(text: String) {
        _messages.tryEmit(text)
    }

    private fun perform(
        origin: WorkspaceSession,
        returnToBrowse: Boolean = false,
        action: suspend () -> Unit,
    ) {
        if (_busy.value) return
        _busy.value = true
        _manager.value = _manager.value?.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = coroutineResult { action() }
                if (repository.workspaces.isCurrent(origin)) {
                    val editor = _manager.value?.takeIf { it.origin == origin }
                    val error = result.exceptionOrNull()?.let(::deckFailureMessage)
                    if (editor == null) error?.let(::message)
                    else
                        _manager.value =
                            editor.copy(
                                page =
                                    if (result.isSuccess && returnToBrowse) DeckEditorPage.Browse
                                    else editor.page,
                                error = error,
                            )
                } else message("Workspace changed. Open the deck again.")
            } finally {
                _busy.value = false
                _manager.value = _manager.value?.copy(busy = false)
            }
        }
    }
}

internal fun deckFailureMessage(error: Throwable): String =
    when ((error as? DeckEditException)?.reason) {
        DeckEditFailure.INVALID_NAME ->
            "Use a deck name of 1–80 characters, without control characters."
        DeckEditFailure.DUPLICATE_NAME -> "A deck already uses this name. Choose another name."
        DeckEditFailure.TOO_MANY_DECKS -> "This workspace can hold up to 100 named decks."
        DeckEditFailure.INVALID_MEMBERS -> "A deck can hold up to 2,000 valid scene names."
        DeckEditFailure.DECK_MISSING -> "This deck was removed. Open another deck."
        DeckEditFailure.DECK_CHANGED ->
            "Decks changed while this editor was open. Reopen it to use the latest version."
        DeckEditFailure.INVALID_FILE ->
            "Choose a SceneDeck decks JSON file, version 1, up to 4 MiB."
        null -> "Couldn't save decks. Retry in the current workspace."
    }
