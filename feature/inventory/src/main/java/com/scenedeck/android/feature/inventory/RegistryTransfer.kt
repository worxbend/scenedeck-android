package com.scenedeck.android.feature.inventory

import android.content.ContentResolver
import android.net.Uri
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.RegistryExportCodec
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.WorkspaceChangedException
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.importMerge
import com.scenedeck.android.core.data.requireCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Parsed curation and its original destination, retained together through preview/confirmation. */
data class ImportPreview(
    val origin: WorkspaceSession,
    val entries: List<SceneRegistryEntry>,
    val newCount: Int,
    val updateCount: Int,
    val recovery: Boolean = false,
)

class RegistryTransfer(
    private val registry: RegistryRepository,
    private val scope: CoroutineScope,
    private val messages: MutableSharedFlow<String>,
) {
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()
    private var exportOrigin: WorkspaceSession? = null
    private var importOrigin: WorkspaceSession? = null

    /** Capture before opening the document picker, not after it returns. */
    fun beginExport(origin: WorkspaceSession): Boolean {
        exportOrigin = origin.takeIf(registry.workspaces::isCurrent)
        return exportOrigin != null
    }

    fun beginImport(origin: WorkspaceSession): Boolean {
        importOrigin = origin.takeIf(registry.workspaces::isCurrent)
        return importOrigin != null
    }

    suspend fun encode(origin: WorkspaceSession): String =
        RegistryExportCodec.encode(registry.snapshot(origin))

    fun exportTo(uri: Uri, resolver: ContentResolver) {
        val origin = exportOrigin
        exportOrigin = null
        scope.launch {
            coroutineResult {
                val encoded = encode(origin ?: throw WorkspaceChangedException())
                withContext(Dispatchers.IO) {
                    registry.workspaces.requireCurrent(origin)
                    resolver.openOutputStream(uri)?.use { it.write(encoded.toByteArray()) }
                        ?: error("Cannot open export target")
                }
            }
                .onSuccess { messages.emit("Registry exported") }
                .onFailure {
                    messages.emit(
                        "Export failed. Check the workspace and selected document, then try again."
                    )
                }
        }
    }

    fun importFrom(uri: Uri, resolver: ContentResolver) {
        val origin = importOrigin
        importOrigin = null
        scope.launch {
            coroutineResult {
                if (origin == null) throw WorkspaceChangedException()
                registry.workspaces.requireCurrent(origin)
                val payload =
                    withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use(::readRegistryDocument)
                            ?: error("Cannot open registry")
                    }
                stage(payload, origin)
            }
                .onFailure {
                    messages.emit(
                        "Import failed. Check the workspace and choose the document again."
                    )
                }
        }
    }

    suspend fun stage(payload: String, origin: WorkspaceSession) {
        coroutineResult {
            registry.workspaces.requireCurrent(origin)
            require(payload.length <= MAX_REGISTRY_DOCUMENT_BYTES)
            preview(origin, RegistryExportCodec.decode(payload))
        }
            .onFailure {
                messages.emit(
                    "Import failed. Check the workspace and choose a valid registry document."
                )
            }
    }

    /** Explicit copy; the original global records are never removed or automatically assigned. */
    fun copyPreviousCuration(origin: WorkspaceSession) {
        scope.launch {
            coroutineResult { preview(origin, registry.legacyEntries.first(), recovery = true) }
                .onFailure {
                    messages.emit(
                        "Couldn't load previous curation. Check the workspace and try again."
                    )
                }
        }
    }

    private suspend fun preview(
        origin: WorkspaceSession,
        entries: List<SceneRegistryEntry>,
        recovery: Boolean = false,
    ) {
        val names = registry.snapshot(origin).map { it.sceneName }.toMutableSet()
        val newCount = entries.count { names.add(it.sceneName) }
        registry.workspaces.requireCurrent(origin)
        _importPreview.value =
            ImportPreview(origin, entries, newCount, entries.size - newCount, recovery)
    }

    fun dismissPreview() {
        if (!_importing.value) _importPreview.value = null
    }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        if (!_importing.compareAndSet(false, true)) return
        scope.launch {
            try {
                coroutineResult { registry.importMerge(preview.origin, preview.entries) }
                    .onSuccess { (inserted, updated) ->
                        if (_importPreview.value === preview) _importPreview.value = null
                        messages.emit("Imported $inserted new, updated $updated entries")
                    }
                    .onFailure { error ->
                        if (error is WorkspaceChangedException) {
                            if (_importPreview.value === preview) _importPreview.value = null
                            messages.emit(
                                "Workspace changed. Start the import again in its intended workspace."
                            )
                        } else messages.emit("Import failed. Your preview is kept; try again.")
                    }
            } finally {
                _importing.value = false
            }
        }
    }
}
