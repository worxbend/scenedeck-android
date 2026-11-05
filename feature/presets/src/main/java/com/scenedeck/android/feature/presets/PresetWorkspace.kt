package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.PresetRepository
import com.scenedeck.android.core.data.PresetState
import com.scenedeck.android.core.data.PresetTargets
import com.scenedeck.android.core.data.PresetTargetsRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

internal data class PresetWorkspaceUi(
    val saved: PresetState = PresetState.Unavailable(WorkspaceState.Unavailable),
    val targets: PresetTargets = PresetTargets.Unavailable,
    val scenes: List<SceneCardState>? = null,
    val profileNames: Map<Long, String> = emptyMap(),
) {
    val aliases =
        scenes.orEmpty().mapNotNull { scene -> scene.localAlias?.let { scene.name to it } }.toMap()
    val sceneNames: Set<String>? = scenes?.mapTo(mutableSetOf()) { it.name }
    val inputOrigin = (targets as? PresetTargets.Ready)?.origin
    val inputNames: Set<String>? =
        (targets as? PresetTargets.Ready)?.inputs?.mapTo(mutableSetOf()) { it.name }
    val ready: PresetState.Ready?
        get() = saved as? PresetState.Ready

    fun title(origin: WorkspaceSession) =
        "${profileNames[origin.key.profileId] ?: "Connection #${origin.key.profileId}"} · ${origin.key.collectionName}"
}

/** One context-checked projection; editor names never depend on the selected program scene. */
class PresetWorkspace
@Inject
constructor(
    repository: PresetRepository,
    private val targets: PresetTargetsRepository,
    scenes: ObsStateRepository,
    profiles: ProfileRepository,
) {
    internal val state =
        combine(repository.state, targets.state, scenes.deckState, profiles.profiles) {
            saved,
            directory,
            deck,
            connections ->
            val ready =
                (saved as? PresetState.Ready)?.takeIf { repository.workspaces.isCurrent(it.origin) }
            val coherent =
                if (saved is PresetState.Ready && ready == null)
                    PresetState.Unavailable(repository.workspaces.state.value)
                else saved
            PresetWorkspaceUi(
                coherent,
                directory,
                deck.allScenes.takeIf {
                    ready != null &&
                        deck.workspace == ready.origin &&
                        !deck.scenesFailed &&
                        !deck.curationFailed
                },
                connections.associate { it.id to it.name },
            )
        }

    fun retryTargets() = targets.retry()
}
