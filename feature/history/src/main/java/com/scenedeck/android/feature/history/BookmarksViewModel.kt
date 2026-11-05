package com.scenedeck.android.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.BookmarksRepository
import com.scenedeck.android.core.data.BookmarksState
import com.scenedeck.android.core.data.LocalBookmark
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class BookmarkOperation(
    val saving: Boolean = false,
    val unsaved: LocalBookmark? = null,
    val message: String? = null,
)

internal data class BookmarksUiState(
    val saved: BookmarksState = BookmarksState.Loading,
    val workspace: WorkspaceSession? = null,
    val connectionNames: Map<Long, String> = emptyMap(),
    val operation: BookmarkOperation = BookmarkOperation(),
    val workspaceState: WorkspaceState = WorkspaceState.Unavailable,
)

@HiltViewModel
class BookmarksViewModel
@Inject
constructor(
    private val repository: BookmarksRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    private val operation = MutableStateFlow(BookmarkOperation())
    internal val uiState =
        combine(repository.state, repository.workspaces.state, profiles.profiles, operation) {
                saved,
                workspace,
                profilesList,
                pending ->
                BookmarksUiState(
                    saved,
                    (workspace as? WorkspaceState.Ready)
                        ?.session
                        ?.takeIf(repository.workspaces::isCurrent),
                    profilesList.associate { it.id to it.name },
                    pending,
                    workspace,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    internal fun mark(origin: WorkspaceSession) {
        if (operation.value.saving || operation.value.unsaved != null) return
        val captured =
            try {
                repository.capture(origin)
            } catch (_: IllegalStateException) {
                operation.value =
                    BookmarkOperation(message = "Connection changed. No bookmark was captured.")
                return
            }
        save(captured)
    }

    internal fun retrySave() {
        operation.value.unsaved?.let(::save)
    }

    internal fun discard() {
        if (!operation.value.saving) operation.value = BookmarkOperation()
    }

    internal fun retryLoad() = repository.retry()

    private fun save(bookmark: LocalBookmark) {
        if (operation.value.saving) return
        operation.value = BookmarkOperation(saving = true, unsaved = bookmark)
        viewModelScope.launch {
            try {
                val saved = coroutineResult { repository.save(bookmark) }.isSuccess
                operation.value =
                    if (saved) BookmarkOperation(message = "Bookmark saved on this device.")
                    else
                        BookmarkOperation(
                            unsaved = bookmark,
                            message =
                                "Couldn't save this moment. Retry keeps its original timestamp.",
                        )
            } finally {
                operation.value = operation.value.copy(saving = false)
            }
        }
    }

    internal suspend fun rename(bookmark: LocalBookmark, label: String): Boolean = coroutineResult {
        repository.rename(bookmark, label)
    }
        .isSuccess

    internal fun delete(bookmark: LocalBookmark) {
        viewModelScope.launch {
            if (coroutineResult { repository.delete(bookmark) }.isFailure)
                operation.value =
                    operation.value.copy(message = "Couldn't delete the bookmark. Try again.")
        }
    }
}
