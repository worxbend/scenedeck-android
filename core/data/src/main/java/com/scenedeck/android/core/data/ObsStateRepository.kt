package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.TransitionInfo
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.withSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One scene card on the deck (OBS scene + local registry metadata). */
data class SceneCardState(
    val name: String,
    val role: SceneRole,
    val accentColorArgb: Long?,
    /** SceneIcon catalogue entry name (null = default clapperboard). */
    val iconName: String?,
    val sortOrder: Int,
    /** On program (red tally). */
    val isActive: Boolean,
    /** On preview (studio mode only, green). */
    val isPreview: Boolean = false,
)

/** Everything the Live deck renders. */
data class DeckState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** PRIMARY scenes only, registry order then name. */
    val scenes: List<SceneCardState> = emptyList(),
    val currentProgramScene: String? = null,
    /** OBS studio mode flag (preview/program workflow). */
    val studioMode: Boolean = false,
    /** Current preview scene (studio mode only). */
    val previewScene: String? = null,
    val currentTransition: CurrentTransition? = null,
    val transitions: List<TransitionInfo> = emptyList(),
    /** All OBS scenes, with local metadata, in registry order. */
    val allScenes: List<SceneCardState> = scenes,
    /** Present only when scenes and curation belong to the same current workspace. */
    val workspace: WorkspaceSession? = null,
    val workspaceState: WorkspaceState = WorkspaceState.Unavailable,
    val curationFailed: Boolean = false,
    val scenesFailed: Boolean = false,
)

/** Combines only snapshots from the same authoritative workspace. */
@Singleton
@Suppress("TooManyFunctions") // deck state + studio control surface
class ObsStateRepository
@Inject
constructor(
    private val client: ObsClient,
    private val registry: RegistryRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private class SceneContext(val origin: WorkspaceSession) {
        val renames = SceneRenameLog()
    }

    private data class Snapshot(
        val context: SceneContext? = null,
        val scenes: List<SceneSummary>? = null,
        val scenesFailed: Boolean = false,
        val program: String? = null,
        val programRevision: Long = 0,
        val listRevision: Long = 0,
        val studioMode: Boolean = false,
        val preview: String? = null,
        val transition: CurrentTransition? = null,
        val transitions: List<TransitionInfo> = emptyList(),
    )

    private val snapshot = MutableStateFlow(Snapshot())

    val deckState: StateFlow<DeckState> =
        combine(client.connectionState, snapshot, registry.state, registry.workspaces.state) {
                connection,
                scene,
                metadata,
                workspace ->
                buildDeck(connection, scene, metadata, workspace)
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DeckState())

    init {
        // Subscribe before initial discovery, including when using in-memory Flow adapters.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.eventsWithContext.collect { received ->
                val context = snapshot.value.context ?: return@collect
                if (received.token == context.origin.token && isCurrent(context)) {
                    reduce(context, received.event)
                }
            }
        }
        scope.launch {
            combine(registry.workspaces.state, client.connectionState) { workspace, connection ->
                    workspace to connection
                }
                .collectLatest { (state, connection) ->
                    val origin =
                        (state as? WorkspaceState.Ready)
                            ?.session
                            ?.takeIf(registry.workspaces::isCurrent)
                    val ready = connection as? ConnectionState.Ready
                    val context =
                        origin
                            ?.takeIf { ready?.token == it.token && !ready.collectionChanging }
                            ?.let(::SceneContext)
                    snapshot.value = Snapshot(context)
                    if (context != null) refresh(context)
                }
        }
    }

    private fun buildDeck(
        connection: ConnectionState,
        scene: Snapshot,
        metadata: RegistryState,
        workspace: WorkspaceState,
    ): DeckState {
        val context = scene.context
        val ready = metadata as? RegistryState.Ready
        val usable =
            context != null &&
                isCurrent(context) &&
                ready?.session == context.origin &&
                scene.scenes != null
        val base =
            DeckState(
                connectionState = connection,
                workspaceState = workspace,
                curationFailed = metadata is RegistryState.Failed,
                scenesFailed = scene.scenesFailed,
            )
        if (!usable) return base
        val entries = requireNotNull(ready).entries.associateBy { it.sceneName }
        val cards =
            requireNotNull(scene.scenes)
                .sortedWith(compareBy({ -it.index }, { it.name }))
                .map { item ->
                    val entry = entries[item.name]
                    SceneCardState(
                        item.name,
                        entry?.role ?: SceneRole.PRIMARY,
                        entry?.accentColorArgb,
                        entry?.iconName,
                        entry?.sortOrder ?: UNPLACED_SORT_ORDER,
                        item.name == scene.program,
                        scene.studioMode && item.name == scene.preview,
                    )
                }
                .sortedBy { it.sortOrder }
        return base.copy(
            workspace = context.origin,
            scenes = cards.filter { it.role == SceneRole.PRIMARY },
            allScenes = cards,
            currentProgramScene = scene.program,
            studioMode = scene.studioMode,
            previewScene = scene.preview,
            currentTransition = scene.transition,
            transitions = scene.transitions,
        )
    }

    private fun isCurrent(context: SceneContext): Boolean =
        snapshot.value.context === context && registry.workspaces.isCurrent(context.origin)

    private fun update(context: SceneContext, change: (Snapshot) -> Snapshot) {
        snapshot.update { current ->
            if (current.context === context && registry.workspaces.isCurrent(context.origin))
                change(current)
            else current
        }
    }

    private suspend fun reduce(context: SceneContext, event: ObsEvent) {
        when (event) {
            is ObsEvent.CurrentProgramSceneChanged ->
                update(context) {
                    it.copy(program = event.sceneName, programRevision = it.programRevision + 1)
                }
            is ObsEvent.StudioModeStateChanged -> {
                update(context) { it.copy(studioMode = event.enabled) }
                refreshStudio(context)
            }
            is ObsEvent.CurrentPreviewSceneChanged ->
                update(context) { it.copy(preview = event.sceneName) }
            is ObsEvent.CurrentSceneTransitionChanged,
            is ObsEvent.CurrentSceneTransitionDurationChanged -> refreshTransition(context)
            is ObsEvent.SceneNameChanged -> applyRename(context, event)
            is ObsEvent.SceneListChanged ->
                update(context) {
                    it.copy(
                        scenes = event.scenes,
                        scenesFailed = false,
                        listRevision = it.listRevision + 1,
                    )
                }
            else -> Unit
        }
    }

    private suspend fun applyRename(context: SceneContext, event: ObsEvent.SceneNameChanged) {
        val (old, new) = event.oldSceneName to event.sceneName
        if (old == new) return
        context.renames.record(old, new)
        update(context) { current ->
            current.copy(
                scenes = current.scenes?.map { if (it.name == old) it.copy(name = new) else it },
                program = if (current.program == old) new else current.program,
                preview = if (current.preview == old) new else current.preview,
            )
        }
        requestResult { registry.rename(context.origin, old, new) }
        migrateMixerSelection(context, old, new)
    }

    private suspend fun migrateMixerSelection(context: SceneContext, old: String, new: String) {
        requestResult {
            settings.update {
                registry.workspaces.requireCurrent(context.origin)
                if (it.selectionBelongsTo(context.origin.key) && it.mixerSelectedScene == old)
                    it.copy(mixerSelectedScene = new)
                else it
            }
        }
    }

    private suspend fun refresh(context: SceneContext) {
        val before = snapshot.value
        requestResult {
            var mark = context.renames.mark()
            var list = read(context) { getSceneList() }
            var rereads = 0
            while (context.renames.mark() != mark && rereads++ < MAX_RENAME_REREADS) {
                mark = context.renames.mark()
                list = read(context) { getSceneList() }
            }
            publishSceneList(context, before, list, mark)
            if (context.renames.mark() != mark && isCurrent(context))
                scope.launch { refresh(context) }
            refreshStudio(context)
        }
            .onFailure { update(context) { it.copy(scenesFailed = true) } }
    }

    private fun publishSceneList(
        context: SceneContext,
        before: Snapshot,
        list: com.scenedeck.android.core.model.SceneListSnapshot,
        mark: Long,
    ) {
        update(context) {
            it.copy(
                scenes =
                    if (it.listRevision == before.listRevision)
                        context.renames.followScenes(list.scenes, mark)
                    else it.scenes,
                scenesFailed = false,
                program =
                    if (it.programRevision == before.programRevision)
                        context.renames.follow(list.currentProgramScene, mark)
                    else it.program,
            )
        }
    }

    private suspend fun refreshStudio(context: SceneContext) {
        requestResult {
            val enabled = read(context) { getStudioModeEnabled() }
            update(context) { it.copy(studioMode = enabled) }
            if (enabled) {
                val mark = context.renames.mark()
                val preview = read(context) { getCurrentPreviewScene() }
                update(context) { it.copy(preview = context.renames.follow(preview, mark)) }
            } else update(context) { it.copy(preview = null) }
        }
        refreshTransition(context)
    }

    private suspend fun refreshTransition(context: SceneContext) {
        requestResult {
            val transition = read(context) { getCurrentSceneTransition() }
            val list = read(context) { getSceneTransitionList() }.transitions
            update(context) { it.copy(transition = transition, transitions = list) }
        }
    }

    private suspend fun <T> read(context: SceneContext, action: suspend ObsClient.() -> T): T =
        inWorkspace(context.origin, action)

    private suspend fun <T> inWorkspace(
        origin: WorkspaceSession,
        action: suspend ObsClient.() -> T,
    ): T {
        registry.workspaces.requireCurrent(origin)
        val result = client.withSession(origin.token, action)
        registry.workspaces.requireCurrent(origin)
        return result
    }

    fun isWorkspaceCurrent(origin: WorkspaceSession): Boolean =
        registry.workspaces.isCurrent(origin)

    fun retryWorkspace() {
        registry.retry()
        snapshot.value.context?.takeIf(::isCurrent)?.let { context ->
            scope.launch { refresh(context) }
        }
    }

    suspend fun setStudioModeEnabled(origin: WorkspaceSession, enabled: Boolean) =
        inWorkspace(origin) { setStudioModeEnabled(enabled) }

    suspend fun setCurrentPreviewScene(origin: WorkspaceSession, sceneName: String) =
        inWorkspace(origin) { setCurrentPreviewScene(sceneName) }

    suspend fun triggerStudioModeTransition(origin: WorkspaceSession) =
        inWorkspace(origin) { triggerStudioModeTransition() }

    suspend fun setCurrentSceneTransition(origin: WorkspaceSession, transitionName: String) =
        inWorkspace(origin) { setCurrentSceneTransition(transitionName) }

    suspend fun setCurrentSceneTransitionDuration(origin: WorkspaceSession, durationMs: Int) =
        inWorkspace(origin) { setCurrentSceneTransitionDuration(durationMs) }

    suspend fun setCurrentProgramScene(origin: WorkspaceSession, sceneName: String) =
        inWorkspace(origin) { setCurrentProgramScene(sceneName) }

    suspend fun updateSceneMeta(
        origin: WorkspaceSession,
        sceneName: String,
        role: SceneRole,
        accentColorArgb: Long?,
        iconName: String?,
    ) = registry.update(origin, sceneName, role, accentColorArgb, iconName)

    suspend fun reorderDeck(origin: WorkspaceSession, orderedSceneNames: List<String>) =
        registry.reorder(origin, orderedSceneNames)

    suspend fun toggleVirtualCam(): Boolean = client.toggleVirtualCam()

    suspend fun setSceneItemEnabled(
        origin: WorkspaceSession,
        sceneName: String,
        sceneItemId: Int,
        enabled: Boolean,
    ) = inWorkspace(origin) { setSceneItemEnabled(sceneName, sceneItemId, enabled) }

    suspend fun getSceneItemList(origin: WorkspaceSession, sceneName: String) =
        inWorkspace(origin) { getSceneItemList(sceneName) }

    private companion object {
        const val MAX_RENAME_REREADS = 2
    }
}
