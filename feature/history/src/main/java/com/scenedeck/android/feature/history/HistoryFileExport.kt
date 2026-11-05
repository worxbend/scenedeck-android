package com.scenedeck.android.feature.history

import android.content.ContentResolver
import android.net.Uri
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.HistoryRepository
import com.scenedeck.android.core.data.WallClock
import com.scenedeck.android.core.data.exportJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class HistoryExportState(val busy: Boolean = false, val request: String? = null)

/** Prepared before the picker; changing selection or OBS context cannot retarget the export. */
internal class HistoryFileExport(
    private val repository: HistoryRepository,
    private val clock: WallClock,
    private val scope: CoroutineScope,
    private val message: (String) -> Unit,
) {
    private val current = MutableStateFlow(HistoryExportState())
    val state = current.asStateFlow()
    private var prepared: String? = null
    private var writing = false

    fun prepare(id: String?) {
        if (current.value.busy) return
        current.value = HistoryExportState(busy = true)
        scope.launch {
            val now = clock.nowMs()
            coroutineResult {
                withContext(Dispatchers.Default) { repository.exportSnapshot(id).exportJson(now) }
            }
                .onSuccess {
                    prepared = it
                    current.value = HistoryExportState(true, "scenedeck-history-$now.json")
                }
                .onFailure {
                    current.value = HistoryExportState()
                    message(
                        "Couldn't prepare the export. Check saved history and bookmarks, then retry."
                    )
                }
        }
    }

    fun pickerOpened() {
        current.value = current.value.copy(request = null)
    }

    fun pickerFailed() {
        prepared = null
        current.value = HistoryExportState()
        message("Couldn't open the document picker. Try exporting again.")
    }

    fun writeTo(uri: Uri?, resolver: ContentResolver) {
        if (writing) return
        val snapshot = prepared
        prepared = null
        when {
            uri == null -> current.value = HistoryExportState()
            snapshot == null -> {
                current.value = HistoryExportState()
                message("The export expired. Prepare it again from History.")
            }
            else -> writeSnapshot(snapshot, uri, resolver)
        }
    }

    private fun writeSnapshot(snapshot: String, uri: Uri, resolver: ContentResolver) {
        current.value = HistoryExportState(busy = true)
        writing = true
        scope.launch {
            try {
                coroutineResult {
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(uri, "wt")?.use {
                            it.write(snapshot.toByteArray(Charsets.UTF_8))
                        } ?: error("Export target unavailable")
                    }
                }
                    .onSuccess { message("History exported to the selected document.") }
                    .onFailure {
                        message(
                            "Couldn't write the export. The selected document may be incomplete; try exporting again."
                        )
                    }
            } finally {
                writing = false
                current.value = HistoryExportState()
            }
        }
    }
}
