package com.scenedeck.android.feature.history

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.HistoryCaptureState
import com.scenedeck.android.core.data.HistoryDetailState
import com.scenedeck.android.core.data.HistoryRecorder
import com.scenedeck.android.core.data.HistoryRepository
import com.scenedeck.android.core.data.HistoryState
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class HistorySelection(val id: String? = null, val detail: HistoryDetailState? = null)

internal data class HistoryOperation(val busy: Boolean = false, val message: String? = null)

internal data class HistoryUiState(
    val saved: HistoryState = HistoryState.Loading,
    val capture: HistoryCaptureState = HistoryCaptureState(),
    val connectionNames: Map<Long, String> = emptyMap(),
    val selection: HistorySelection = HistorySelection(),
    val operation: HistoryOperation = HistoryOperation(),
    val export: HistoryExportState = HistoryExportState(),
) {
    val busy
        get() = operation.busy || export.busy
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel
@Inject
constructor(
    private val savedState: SavedStateHandle,
    private val repository: HistoryRepository,
    private val recorder: HistoryRecorder,
    profiles: ProfileRepository,
    clock: WallClock,
) : ViewModel() {
    private val operation = MutableStateFlow(HistoryOperation())
    private val files =
        HistoryFileExport(repository, clock, viewModelScope) {
            operation.value = operation.value.copy(message = it)
        }
    private val selection =
        savedState.getStateFlow<String?>("observation", null).flatMapLatest { id ->
            if (id == null) flowOf(HistorySelection())
            else repository.detail(id).map { HistorySelection(id, it) }
        }
    private val stored =
        combine(repository.state, recorder.state, profiles.profiles) { saved, capture, connections
            ->
            HistoryUiState(saved, capture, connections.associate { it.id to it.name })
        }
    internal val uiState =
        combine(stored, selection, operation, files.state) { stored, selected, action, export ->
                stored.copy(selection = selected, operation = action, export = export)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    internal fun select(id: String?) {
        savedState["observation"] = id
    }

    internal fun configure(enabled: Boolean, limit: Int) =
        mutate("History settings saved.") {
            repository.configure(enabled, limit)
        }

    internal fun delete(id: String?) =
        mutate("History deleted. Saved bookmarks remain in Bookmarks.") {
            repository.delete(id)
            if (id == null || savedState.get<String>("observation") == id) select(null)
        }

    internal fun retry() {
        repository.retry()
        recorder.retry()
    }

    internal fun export(id: String?) {
        if (!operation.value.busy) files.prepare(id)
    }

    internal fun pickerOpened() = files.pickerOpened()

    internal fun pickerFailed() = files.pickerFailed()

    internal fun fileResult(uri: Uri?, resolver: ContentResolver) = files.writeTo(uri, resolver)

    private fun mutate(success: String, block: suspend () -> Unit) {
        if (operation.value.busy || files.state.value.busy) return
        operation.value = HistoryOperation(busy = true)
        viewModelScope.launch {
            try {
                val result = coroutineResult { block() }
                operation.value =
                    HistoryOperation(
                        message =
                            if (result.isSuccess) success
                            else
                                "Couldn't update history. Your saved data may have changed; try again."
                    )
            } finally {
                operation.value = operation.value.copy(busy = false)
            }
        }
    }
}
