package com.scenedeck.android.core.data

const val MAX_SCENE_ALIAS_LENGTH = 80

class SceneAliasException : IllegalArgumentException("Invalid local scene label")

fun checkedSceneAlias(value: String?): String? {
    val alias = value?.trim()?.takeIf { it.isNotEmpty() }
    if (alias != null && (alias.length > MAX_SCENE_ALIAS_LENGTH || alias.any(Char::isISOControl)))
        throw SceneAliasException()
    return alias
}

/** Full quick-edit draft; a nullable role preserves an explicitly unassigned scene. */
data class SceneMetadataEdit(
    val role: SceneRole?,
    val accentColorArgb: Long?,
    val iconName: String?,
    val localAlias: String?,
)
