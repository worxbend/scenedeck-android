package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.SceneRegistryEntity

/** Local curation roles for scenes (FEATURE_SPEC §6). The deck shows PRIMARY only. */
enum class SceneRole {
    PRIMARY,
    SECONDARY,
    MODULE,
    RAW,
    DEBUG,
    ARCHIVE,
}

/** Local curation metadata for one OBS scene. Never synced back to OBS. */
data class SceneRegistryEntry(
    val sceneName: String,
    val role: SceneRole,
    val accentColorArgb: Long? = null,
    val iconName: String? = null,
    val sortOrder: Int = 0,
)

internal fun SceneRegistryEntity.toDomain() = SceneRegistryEntry(
    sceneName = sceneName,
    role = runCatching { SceneRole.valueOf(role) }.getOrDefault(SceneRole.PRIMARY),
    accentColorArgb = accentColorArgb,
    iconName = iconName,
    sortOrder = sortOrder,
)

internal fun SceneRegistryEntry.toEntity() = SceneRegistryEntity(
    sceneName = sceneName,
    role = role.name,
    accentColorArgb = accentColorArgb,
    iconName = iconName,
    sortOrder = sortOrder,
)
