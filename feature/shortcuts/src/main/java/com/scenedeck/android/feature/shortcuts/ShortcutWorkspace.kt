package com.scenedeck.android.feature.shortcuts

import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.PresetTargets
import com.scenedeck.android.core.data.PresetTargetsRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutRepository
import com.scenedeck.android.core.data.ShortcutState
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.ui.ObsTargetChoice
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

internal data class ShortcutWorkspaceUi(
    val saved: ShortcutState = ShortcutState.Unavailable(WorkspaceState.Unavailable),
    val targets: PresetTargets = PresetTargets.Unavailable,
    val scenes: List<SceneCardState>? = null,
    val profiles: Map<Long, String> = emptyMap(),
) {
    val ready: ShortcutState.Ready?
        get() = saved as? ShortcutState.Ready

    fun title(origin: WorkspaceSession) =
        "${profiles[origin.key.profileId] ?: "Connection #${origin.key.profileId}"} · ${origin.key.collectionName}"

    fun alias(name: String) = scenes?.find { it.name == name }?.localAlias

    fun choices(origin: WorkspaceSession, scene: Boolean): List<ObsTargetChoice>? =
        when {
            ready?.origin != origin -> null
            scene -> scenes?.map { ObsTargetChoice(it.name, it.displayName, "OBS: ${it.name}") }
            else ->
                (targets as? PresetTargets.Ready)
                    ?.takeIf { it.origin == origin }
                    ?.inputs
                    ?.map { ObsTargetChoice(it.name, it.name, null) }
        }

    fun missing(action: ShortcutAction): Boolean {
        val name = action.targetName()
        val origin = ready?.origin ?: return false
        return choices(origin, action is ShortcutAction.Scene)?.none { it.name == name } == true &&
            name != null
    }
}

class ShortcutWorkspace
@Inject
constructor(
    repository: ShortcutRepository,
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
                (saved as? ShortcutState.Ready)?.takeIf {
                    repository.workspaces.isCurrent(it.origin)
                }
            val coherent =
                if (saved is ShortcutState.Ready && ready == null)
                    ShortcutState.Unavailable(repository.workspaces.state.value)
                else saved
            ShortcutWorkspaceUi(
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
