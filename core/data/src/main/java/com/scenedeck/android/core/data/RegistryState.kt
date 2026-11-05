package com.scenedeck.android.core.data

/** Unknown/failed metadata is distinct from a successfully loaded empty registry. */
sealed interface RegistryState {
    data class Unavailable(val workspace: WorkspaceState) : RegistryState

    data class Loading(val session: WorkspaceSession) : RegistryState

    data class Ready(val session: WorkspaceSession, val entries: List<SceneRegistryEntry>) :
        RegistryState

    data class Failed(val session: WorkspaceSession) : RegistryState
}
