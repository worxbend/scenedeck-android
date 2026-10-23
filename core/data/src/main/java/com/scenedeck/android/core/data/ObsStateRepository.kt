package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.TransitionInfo
import com.scenedeck.android.core.obs.ObsClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
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
)

/**
 * Combines the OBS scene list/program state with the local registry into the deck model
 * (docs/ARCHITECTURE.md rule 2). M3 DEFAULT RULE: scenes without a registry entry are treated as
 * PRIMARY so the deck shows everything until Inventory (M5).
 */
@Singleton
@Suppress("TooManyFunctions") // deck state + studio control surface
class ObsStateRepository
@Inject
constructor(
    private val client: ObsClient,
    private val registry: RegistryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val sceneList = MutableStateFlow<List<SceneSummary>>(emptyList())
    private val programScene = MutableStateFlow<String?>(null)
    private val studioMode = MutableStateFlow(false)
    private val previewScene = MutableStateFlow<String?>(null)
    private val currentTransition = MutableStateFlow<CurrentTransition?>(null)
    private val transitions = MutableStateFlow<List<TransitionInfo>>(emptyList())
    private val renameLog = SceneRenameLog()

    private data class StudioSnapshot(
        val studioMode: Boolean,
        val previewScene: String?,
        val currentTransition: CurrentTransition?,
        val transitions: List<TransitionInfo>,
    )

    private val studio =
        combine(
            studioMode,
            previewScene,
            currentTransition,
            transitions,
        ) { mode, preview, transition, list ->
            StudioSnapshot(mode, preview, transition, list)
        }

    val deckState: StateFlow<DeckState> =
        combine(
                client.connectionState,
                sceneList,
                programScene,
                registry.entries,
                studio,
            ) { connection, scenes, program, entries, studioSnap ->
                val entriesByName = entries.associateBy { it.sceneName }
                // Saved positions first; unplaced scenes follow OBS's list, whose top scene has
                // the highest sceneIndex.
                val cards =
                    scenes
                        .sortedWith(compareBy({ -it.index }, { it.name }))
                        .map { scene ->
                            val entry = entriesByName[scene.name]
                            SceneCardState(
                                name = scene.name,
                                role = entry?.role ?: SceneRole.PRIMARY,
                                accentColorArgb = entry?.accentColorArgb,
                                iconName = entry?.iconName,
                                sortOrder = entry?.sortOrder ?: UNPLACED_SORT_ORDER,
                                isActive = scene.name == program,
                                isPreview =
                                    studioSnap.studioMode && scene.name == studioSnap.previewScene,
                            )
                        }
                        .sortedBy { it.sortOrder } // stable: ties keep OBS order
                DeckState(
                    connectionState = connection,
                    currentProgramScene = program,
                    studioMode = studioSnap.studioMode,
                    previewScene = studioSnap.previewScene,
                    currentTransition = studioSnap.currentTransition,
                    transitions = studioSnap.transitions,
                    scenes = cards.filter { it.role == SceneRole.PRIMARY },
                    allScenes = cards,
                )
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DeckState())

    init {
        scope.launch {
            client.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Ready -> refresh()
                    ConnectionState.Disconnected -> {
                        renameLog.clear()
                        sceneList.value = emptyList()
                        programScene.value = null
                        studioMode.value = false
                        previewScene.value = null
                        currentTransition.value = null
                        transitions.value = emptyList()
                    }

                    else -> Unit
                }
            }
        }
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ObsEvent.CurrentProgramSceneChanged -> programScene.value = event.sceneName

                    is ObsEvent.StudioModeStateChanged -> {
                        studioMode.value = event.enabled
                        refreshStudio()
                    }

                    is ObsEvent.CurrentPreviewSceneChanged -> previewScene.value = event.sceneName

                    is ObsEvent.CurrentSceneTransitionChanged,
                    is ObsEvent.CurrentSceneTransitionDurationChanged -> refreshTransition()

                    is ObsEvent.SceneTransitionStarted,
                    is ObsEvent.SceneTransitionEnded -> Unit

                    is ObsEvent.SceneNameChanged -> applyRename(event)

                    is ObsEvent.SceneListChanged -> {
                        sceneList.value = event.scenes
                        // Retain local curation across hosts/collections; Doctor offers explicit
                        // cleanup.
                    }

                    else -> Unit
                }
            }
        }
    }

    /**
     * OBS sends no SceneListChanged (nor program/preview change) for renames; follow them locally
     * so taps target the new name, then move the local curation. The log is written before the
     * state so a concurrent read's compare-and-set retry replays it (see [refresh]).
     */
    private suspend fun applyRename(event: ObsEvent.SceneNameChanged) {
        val (old, new) = event.oldSceneName to event.sceneName
        if (old == new) return
        renameLog.record(old, new)
        sceneList.update { list -> list.map { if (it.name == old) it.copy(name = new) else it } }
        programScene.update { if (it == old) new else it }
        previewScene.update { if (it == old) new else it }
        requestResult { registry.rename(old, new) }
    }

    private suspend fun refresh() {
        requestResult {
            var mark = renameLog.mark()
            var list = client.getSceneList()
            // A read that raced a rename may predate it: re-read (OBS answers after the events it
            // already sent), and replay the renames only if they keep racing.
            var rereads = 0
            while (renameLog.mark() != mark && rereads++ < MAX_RENAME_REREADS) {
                mark = renameLog.mark()
                list = client.getSceneList()
            }
            // Compare-and-set: a rename recorded before this commits is replayed on the retry.
            sceneList.update { renameLog.followScenes(list.scenes, mark) }
            programScene.update { renameLog.follow(list.currentProgramScene, mark) }
            // Scene absence on this connection must not delete another host/collection's curation.
            refreshStudio()
        }
    }

    private suspend fun refreshStudio() {
        requestResult {
            studioMode.value = client.getStudioModeEnabled()
            if (studioMode.value) {
                val mark = renameLog.mark()
                val preview = client.getCurrentPreviewScene()
                previewScene.update { renameLog.follow(preview, mark) }
            } else {
                previewScene.value = null
            }
        }
        refreshTransition()
    }

    private suspend fun refreshTransition() {
        requestResult {
            currentTransition.value = client.getCurrentSceneTransition()
            transitions.value = client.getSceneTransitionList().transitions
        }
    }

    suspend fun setStudioModeEnabled(enabled: Boolean) = client.setStudioModeEnabled(enabled)

    suspend fun setCurrentPreviewScene(sceneName: String) = client.setCurrentPreviewScene(sceneName)

    suspend fun triggerStudioModeTransition() = client.triggerStudioModeTransition()

    suspend fun setCurrentSceneTransition(transitionName: String) =
        client.setCurrentSceneTransition(transitionName)

    suspend fun setCurrentSceneTransitionDuration(durationMs: Int) =
        client.setCurrentSceneTransitionDuration(durationMs)

    suspend fun setCurrentProgramScene(sceneName: String) = client.setCurrentProgramScene(sceneName)

    suspend fun updateSceneMeta(
        sceneName: String,
        role: SceneRole,
        accentColorArgb: Long?,
        iconName: String?,
    ) = registry.update(sceneName, role, accentColorArgb, iconName)

    suspend fun reorderDeck(orderedSceneNames: List<String>) = registry.reorder(orderedSceneNames)

    // ── Power features (M7) ─────────────────────────────────────────────────

    suspend fun toggleVirtualCam(): Boolean = client.toggleVirtualCam()

    suspend fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean) =
        client.setSceneItemEnabled(sceneName, sceneItemId, enabled)

    suspend fun getSceneItemList(sceneName: String) = client.getSceneItemList(sceneName)

    private companion object {
        const val MAX_RENAME_REREADS = 2
    }
}
