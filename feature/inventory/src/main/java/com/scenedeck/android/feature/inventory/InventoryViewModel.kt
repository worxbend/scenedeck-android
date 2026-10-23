package com.scenedeck.android.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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

    /** A live scene with no role chosen yet (no entry, or curation without a role). */
    val unassigned: Boolean
        get() = !stale && entry?.role == null

    /** False when the role is only the implicit Primary default (no role chosen). */
    val roleAssigned: Boolean
        get() = entry?.role != null
}

/** A stale entry the user just removed; kept so the snackbar can undo the removal. */
data class RemovedStaleEntry(val entry: SceneRegistryEntry)

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
    private val obsState: ObsStateRepository,
    private val client: ObsClient,
) : ViewModel() {

    val uiState: StateFlow<InventoryUiState> =
        combine(
                client.connectionState,
                obsState.deckState,
                registry.entries,
            ) { connection, deck, entries ->
                // Live scenes in exactly the deck's order; stale entries follow in saved order.
                val byName = entries.associateBy { it.sceneName }
                val live = deck.allScenes.map { it.name }
                val present = live.toSet()
                val scenes =
                    live.map { name -> InventoryScene(name, byName[name], stale = false) } +
                        entries
                            .filter { it.sceneName !in present }
                            .map { InventoryScene(it.sceneName, it, stale = true) }
                InventoryUiState(
                    connection = connection,
                    scenes = scenes,
                    unassignedCount = scenes.count { it.unassigned },
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                InventoryUiState(),
            )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Buffered one-shot events: a removal emitted while no screen collects (rotation) still
    // reaches the next collector, so its Undo is never lost.
    private val _removals = Channel<RemovedStaleEntry>(Channel.BUFFERED)

    /** Stale-entry removals, shown as a snackbar with an Undo action. */
    val removals: Flow<RemovedStaleEntry> = _removals.receiveAsFlow()

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
        launchAction {
            val existing = registry.byName(sceneName)
            registry.update(sceneName, role, existing?.accentColorArgb, existing?.iconName)
        }
    }

    fun setAccent(sceneName: String, accentArgb: Long?) {
        launchAction {
            val existing = registry.byName(sceneName)
            registry.update(
                sceneName,
                existing?.role,
                accentArgb,
                existing?.iconName,
            )
        }
    }

    fun setIcon(sceneName: String, iconName: String?) {
        launchAction {
            val existing = registry.byName(sceneName)
            registry.update(
                sceneName,
                existing?.role,
                existing?.accentColorArgb,
                iconName,
            )
        }
    }

    fun removeStale(sceneName: String) {
        launchAction {
            val removed = registry.remove(sceneName) ?: return@launchAction
            _removals.send(RemovedStaleEntry(removed))
        }
    }

    /** Undo for [removeStale]: puts the entry back with its role, colors, icon and position. */
    fun undoRemoveStale(removed: RemovedStaleEntry) {
        launchAction { registry.restore(removed.entry) }
    }

    fun reorder(orderedSceneNames: List<String>) {
        launchAction { registry.reorder(orderedSceneNames) }
    }

    fun bulkAssignUnassigned() {
        launchAction {
            val names = uiState.value.scenes.filter { it.unassigned }.map { it.name }
            registry.assignRoleToUnassigned(names, SceneRole.SECONDARY)
            _messages.emit("Assigned ${names.size} scenes to Secondary")
        }
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            coroutineResult { action() }
                .onFailure {
                    _messages.emit("Couldn't save scene curation. Please try again.")
                }
        }
    }
}
