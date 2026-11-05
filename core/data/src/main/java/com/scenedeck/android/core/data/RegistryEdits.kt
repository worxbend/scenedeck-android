package com.scenedeck.android.core.data

/** Upserts curation fields while retaining the saved order; null role stays unassigned. */
suspend fun RegistryRepository.update(
    origin: WorkspaceSession,
    sceneName: String,
    role: SceneRole?,
    accentColorArgb: Long?,
    iconName: String?,
) =
    edit(origin, sceneName) {
        it.copy(role = role, accentColorArgb = accentColorArgb, iconName = iconName)
    }

/** A label-only edit never assigns a role or changes the OBS key/order. */
suspend fun RegistryRepository.setAlias(
    origin: WorkspaceSession,
    sceneName: String,
    alias: String?,
) = edit(origin, sceneName) { it.copy(localAlias = checkedSceneAlias(alias)) }
