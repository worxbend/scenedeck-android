package com.scenedeck.android.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.RegistryRepository
import com.scenedeck.android.core.data.RegistryState
import com.scenedeck.android.core.data.SceneRegistryEntry
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.data.assignRoleToUnassigned
import com.scenedeck.android.core.data.checkedSceneAlias
import com.scenedeck.android.core.data.reorder
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
data class RemovedStaleEntry(val origin: WorkspaceSession, val entry: SceneRegistryEntry)

data class InventoryUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val scenes: List<InventoryScene> = emptyList(),
    val unassignedCount: Int = 0,
    val workspace: WorkspaceSession? = null,
    val workspaceState: WorkspaceState = WorkspaceState.Unavailable,
    val failed: Boolean = false,
    val hasLegacyCuration: Boolean = false,
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
                registry.state,
                registry.legacyEntries
                    .map { it.isNotEmpty() }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(false)
                    },
            ) { connection, deck, metadata, hasLegacy ->
                val ready = metadata as? RegistryState.Ready
                val origin = deck.workspace?.takeIf(registry.workspaces::isCurrent)
                if (origin == null || ready?.session != origin)
                    return@combine InventoryUiState(
                        connection = connection,
                        workspaceState = deck.workspaceState,
                        failed = deck.curationFailed || deck.scenesFailed,
                        hasLegacyCuration = hasLegacy,
                    )
                val entries = ready.entries
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
                    workspace = origin,
                    workspaceState = deck.workspaceState,
                    hasLegacyCuration = hasLegacy,
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
    val transfer = RegistryTransfer(registry, viewModelScope, _messages)

    val importPreview: StateFlow<ImportPreview?>
        get() = transfer.importPreview

    suspend fun setAlias(origin: WorkspaceSession, name: String, alias: String?): Boolean =
        coroutineResult {
            registry.edit(origin, name) {
                require(obsState.hasScene(origin, name)) { "Scene is no longer available" }
                it.copy(localAlias = checkedSceneAlias(alias))
            }
        }
        .isSuccess

    fun setRole(origin: WorkspaceSession, sceneName: String, role: SceneRole) {
        launchAction(origin) { bound -> registry.edit(bound, sceneName) { it.copy(role = role) } }
    }

    fun setAccent(origin: WorkspaceSession, sceneName: String, accentArgb: Long?) {
        launchAction(origin) { bound ->
            registry.edit(bound, sceneName) { it.copy(accentColorArgb = accentArgb) }
        }
    }

    fun setIcon(origin: WorkspaceSession, sceneName: String, iconName: String?) {
        launchAction(origin) { bound ->
            registry.edit(bound, sceneName) { it.copy(iconName = iconName) }
        }
    }

    fun removeStale(origin: WorkspaceSession, sceneName: String) {
        launchAction(origin) { bound ->
            val removed = registry.remove(bound, sceneName) ?: return@launchAction
            _removals.send(RemovedStaleEntry(bound, removed))
        }
    }

    fun undoRemoveStale(removed: RemovedStaleEntry) {
        launchAction(removed.origin) { origin -> registry.restore(origin, removed.entry) }
    }

    fun reorder(origin: WorkspaceSession, orderedSceneNames: List<String>) {
        launchAction(origin) { bound -> registry.reorder(bound, orderedSceneNames) }
    }

    fun bulkAssignUnassigned(origin: WorkspaceSession) {
        val names = uiState.value.scenes.filter { it.unassigned }.map { it.name }
        launchAction(origin) { bound ->
            registry.assignRoleToUnassigned(bound, names, SceneRole.SECONDARY)
            _messages.emit("Assigned ${names.size} scenes to Secondary")
        }
    }

    fun retryWorkspace() = obsState.retryWorkspace()

    private fun launchAction(
        origin: WorkspaceSession?,
        action: suspend (WorkspaceSession) -> Unit,
    ) {
        viewModelScope.launch {
            coroutineResult { action(requireNotNull(origin)) }
                .onFailure {
                    _messages.emit("Couldn't save curation. Check the workspace and try again.")
                }
        }
    }
}
