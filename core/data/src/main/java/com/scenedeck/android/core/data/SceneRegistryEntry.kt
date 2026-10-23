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

/** sortOrder of an entry the user never placed; such scenes follow OBS order on the deck. */
const val UNPLACED_SORT_ORDER = Int.MAX_VALUE

/** Stored role of an entry that has curation (order, accent, icon) but no role chosen yet. */
internal const val UNASSIGNED_ROLE = "UNASSIGNED"

/**
 * Local curation metadata for one OBS scene. Never synced back to OBS. A null [role] means
 * "unassigned": the deck treats it as PRIMARY, Inventory and Doctor count it as unassigned.
 */
data class SceneRegistryEntry(
    val sceneName: String,
    val role: SceneRole?,
    val accentColorArgb: Long? = null,
    val iconName: String? = null,
    val sortOrder: Int = 0,
)

internal fun SceneRegistryEntity.toDomain() =
    SceneRegistryEntry(
        sceneName = sceneName,
        role =
            if (role == UNASSIGNED_ROLE) null
            else runCatching { SceneRole.valueOf(role) }.getOrDefault(SceneRole.PRIMARY),
        accentColorArgb = accentColorArgb,
        iconName = iconName,
        sortOrder = sortOrder,
    )

internal fun SceneRegistryEntry.toEntity() =
    SceneRegistryEntity(
        sceneName = sceneName,
        role = role?.name ?: UNASSIGNED_ROLE,
        accentColorArgb = accentColorArgb,
        iconName = iconName,
        sortOrder = sortOrder,
    )
