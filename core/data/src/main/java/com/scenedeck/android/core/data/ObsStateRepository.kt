package com.scenedeck.android.core.data

import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One scene card on the deck (OBS scene + local registry metadata). */
data class SceneCardState(
    val name: String,
    val role: SceneRole,
    val accentColorArgb: Long?,
    /** SceneIcon catalogue entry name (null = default clapperboard). */
    val iconName: String?,
    val sortOrder: Int,
    val isActive: Boolean,
)

/** Everything the Live deck renders. */
data class DeckState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** PRIMARY scenes only, registry order then name. */
    val scenes: List<SceneCardState> = emptyList(),
    val currentProgramScene: String? = null,
)

/**
 * Combines the OBS scene list/program state with the local registry into the deck
 * model (docs/ARCHITECTURE.md rule 2). M3 DEFAULT RULE: scenes without a registry
 * entry are treated as PRIMARY so the deck shows everything until Inventory (M5).
 */
@Singleton
class ObsStateRepository @Inject constructor(
    private val client: ObsClient,
    private val registry: RegistryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val sceneList = MutableStateFlow<List<SceneSummary>>(emptyList())
    private val programScene = MutableStateFlow<String?>(null)

    val deckState: StateFlow<DeckState> = combine(
        client.connectionState,
        sceneList,
        programScene,
        registry.entries,
    ) { connection, scenes, program, entries ->
        DeckState(
            connectionState = connection,
            currentProgramScene = program,
            scenes = scenes.map { scene ->
                val entry = entries.firstOrNull { it.sceneName == scene.name }
                SceneCardState(
                    name = scene.name,
                    role = entry?.role ?: SceneRole.PRIMARY,
                    accentColorArgb = entry?.accentColorArgb,
                    iconName = entry?.iconName,
                    sortOrder = entry?.sortOrder ?: Int.MAX_VALUE,
                    isActive = scene.name == program,
                )
            }
                .filter { it.role == SceneRole.PRIMARY }
                .sortedWith(compareBy({ it.sortOrder }, { it.name })),
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DeckState())

    init {
        scope.launch {
            client.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Ready -> refresh()
                    ConnectionState.Disconnected -> {
                        sceneList.value = emptyList()
                        programScene.value = null
                    }

                    else -> Unit
                }
            }
        }
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ObsEvent.CurrentProgramSceneChanged ->
                        programScene.value = event.sceneName

                    is ObsEvent.SceneListChanged -> {
                        sceneList.value = event.scenes
                        registry.deleteStale(event.scenes.map { it.name })
                    }

                    else -> Unit
                }
            }
        }
    }

    private suspend fun refresh() {
        runCatching {
            val list = client.getSceneList()
            sceneList.value = list.scenes
            programScene.value = list.currentProgramScene
            registry.deleteStale(list.scenes.map { it.name })
        }
    }

    suspend fun setCurrentProgramScene(sceneName: String) = client.setCurrentProgramScene(sceneName)

    suspend fun updateSceneMeta(
        sceneName: String,
        role: SceneRole,
        accentColorArgb: Long?,
        iconName: String?,
    ) = registry.update(sceneName, role, accentColorArgb, iconName)

    suspend fun reorderDeck(orderedSceneNames: List<String>) = registry.reorder(orderedSceneNames)
}
