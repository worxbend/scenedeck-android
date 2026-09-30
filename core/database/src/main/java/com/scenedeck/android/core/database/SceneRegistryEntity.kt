package com.scenedeck.android.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local curation metadata for an OBS scene (FEATURE_SPEC §6). NEVER written back to OBS — the scene
 * collection in OBS stays untouched (project non-negotiable).
 */
@Entity(tableName = "scene_registry")
data class SceneRegistryEntity(
    @PrimaryKey val sceneName: String,
    /** PRIMARY/SECONDARY/MODULE/RAW/DEBUG/ARCHIVE (SceneRole in :core:data). */
    val role: String,
    val accentColorArgb: Long? = null,
    /** SceneIcon catalogue entry name (nullable = default icon). */
    val iconName: String? = null,
    val sortOrder: Int = 0,
)
