package com.scenedeck.android.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.RegistryExportCodec
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the inventory: an OBS scene or a stale registry entry. */
data class InventoryScene(
    val name: String,
    val entry: SceneRegistryEntry?,
    val stale: Boolean,
) {
    val role: SceneRole
        get() = entry?.role ?: SceneRole.PRIMARY
}

/** Staged registry import: merge preview before applying. */
data class ImportPreview(
    val entries: List<SceneRegistryEntry>,
    val newCount: Int,
    val updateCount: Int,
)

data class InventoryUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val scenes: List<InventoryScene> = emptyList(),
    val unassignedCount: Int = 0,
)

@HiltViewModel
class InventoryViewModel
@Inject
constructor(
    private val registry: RegistryRepository,
    private val mixer: MixerRepository,
    private val client: ObsClient,
) : ViewModel() {

    val uiState: StateFlow<InventoryUiState> =
        combine(
                client.connectionState,
                mixer.sceneNames,
                registry.entries,
            ) { connection, sceneNames, entries ->
                val byName = entries.associateBy { it.sceneName }
                val present = sceneNames.toSet()
                val scenes =
                    (sceneNames.map { name ->
                            InventoryScene(name, byName[name], stale = false)
                        } +
                            entries
                                .filter { it.sceneName !in present }
                                .map { InventoryScene(it.sceneName, it, stale = true) })
                        .sortedWith(
                            compareBy(
                                { it.entry?.sortOrder ?: Int.MAX_VALUE },
                                { it.stale },
                                { it.name },
                            )
                        )
                InventoryUiState(
                    connection = connection,
                    scenes = scenes,
                    unassignedCount = scenes.count { it.entry == null && !it.stale },
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                InventoryUiState(),
            )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setRole(sceneName: String, role: SceneRole) {
        viewModelScope.launch {
            val existing = registry.byName(sceneName)
            registry.update(sceneName, role, existing?.accentColorArgb, existing?.iconName)
        }
    }

    fun setAccent(sceneName: String, accentArgb: Long?) {
        viewModelScope.launch {
            val existing = registry.byName(sceneName)
            registry.update(
                sceneName,
                existing?.role ?: SceneRole.PRIMARY,
                accentArgb,
                existing?.iconName,
            )
        }
    }

    fun setIcon(sceneName: String, iconName: String?) {
        viewModelScope.launch {
            val existing = registry.byName(sceneName)
            registry.update(
                sceneName,
                existing?.role ?: SceneRole.PRIMARY,
                existing?.accentColorArgb,
                iconName,
            )
        }
    }

    fun removeStale(sceneName: String) {
        viewModelScope.launch {
            registry.remove(sceneName)
            _messages.tryEmit("Removed stale entry “$sceneName”")
        }
    }

    fun reorder(orderedSceneNames: List<String>) {
        viewModelScope.launch { registry.reorder(orderedSceneNames) }
    }

    fun bulkAssignUnassigned() {
        viewModelScope.launch {
            val names =
                uiState.value.scenes.filter { it.entry == null && !it.stale }.map { it.name }
            registry.assignRoleToUnassigned(names, SceneRole.SECONDARY)
            _messages.tryEmit("Assigned ${names.size} scenes to Secondary")
        }
    }

    suspend fun exportRegistry(): String = RegistryExportCodec.encode(registry.snapshot())

    /** Parsed import awaiting user confirmation (merge preview). */
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    /** Parses the file and stages a preview; malformed files surface as a message. */
    fun stageImport(payload: String) {
        coroutineResult { RegistryExportCodec.decode(payload) }
            .onSuccess { decoded ->
                val known =
                    uiState.value.scenes.mapNotNull { it.entry }.map { it.sceneName }.toSet()
                val newCount = decoded.count { it.sceneName !in known }
                _importPreview.value = ImportPreview(decoded, newCount, decoded.size - newCount)
            }
            .onFailure { _messages.tryEmit("Import failed: ${it.message}") }
    }

    fun dismissImportPreview() {
        _importPreview.value = null
    }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            val (inserted, updated) = registry.importMerge(preview.entries)
            _messages.tryEmit("Imported $inserted new, updated $updated entries")
            _importPreview.value = null
        }
    }
}
