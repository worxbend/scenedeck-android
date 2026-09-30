package com.scenedeck.android.feature.inventory

import android.content.ContentResolver
import android.net.Uri
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.RegistryExportCodec
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRegistryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Staged registry import: merge preview before applying. */
data class ImportPreview(
    val entries: List<SceneRegistryEntry>,
    val newCount: Int,
    val updateCount: Int,
)

/**
 * Registry export/import transfer: document I/O plus merge-preview staging. File work survives
 * configuration changes because it runs on the owner's long-lived [scope]; results surface as
 * messages.
 */
class RegistryTransfer(
    private val registry: RegistryRepository,
    private val scope: CoroutineScope,
    private val messages: MutableSharedFlow<String>,
    private val knownSceneNames: () -> Set<String>,
) {

    /** Parsed import awaiting user confirmation (merge preview). */
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    suspend fun encode(): String = RegistryExportCodec.encode(registry.snapshot())

    /** Writes the encoded registry to a user-picked document. */
    fun exportTo(uri: Uri, resolver: ContentResolver) {
        scope.launch {
            coroutineResult {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(encode().toByteArray())
                    } ?: error("Cannot open export target")
                }
            }
                .onSuccess { messages.emit("Registry exported") }
                .onFailure { messages.emit("Export failed: ${it.message}") }
        }
    }

    /** Reads a user-picked document and stages a merge preview. */
    fun importFrom(uri: Uri, resolver: ContentResolver) {
        scope.launch {
            coroutineResult {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        input.readBytes().decodeToString()
                    } ?: error("Cannot open registry")
                }
            }
                .onSuccess { stage(it) }
                .onFailure { messages.emit("Import failed: ${it.message}") }
        }
    }

    /** Parses the file and stages a preview; malformed files surface as a message. */
    suspend fun stage(payload: String) {
        coroutineResult { RegistryExportCodec.decode(payload) }
            .onSuccess { decoded ->
                val known = knownSceneNames()
                val newCount = decoded.count { it.sceneName !in known }
                _importPreview.value = ImportPreview(decoded, newCount, decoded.size - newCount)
            }
            .onFailure { messages.emit("Import failed: ${it.message}") }
    }

    fun dismissPreview() {
        _importPreview.value = null
    }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        scope.launch {
            val (inserted, updated) = registry.importMerge(preview.entries)
            messages.emit("Imported $inserted new, updated $updated entries")
            _importPreview.value = null
        }
    }
}
