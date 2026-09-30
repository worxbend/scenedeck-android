package com.scenedeck.android.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.MixerRepository
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /**
     * Registry export/import transfer; file work runs on [viewModelScope] so it survives rotation.
     */
    val transfer =
        RegistryTransfer(registry, viewModelScope, _messages) {
            uiState.value.scenes.mapNotNull { it.entry }.map { it.sceneName }.toSet()
        }

    val importPreview: StateFlow<ImportPreview?>
        get() = transfer.importPreview

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
            _messages.emit("Removed stale entry “$sceneName”")
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
            _messages.emit("Assigned ${names.size} scenes to Secondary")
        }
    }
}
