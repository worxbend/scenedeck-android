package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** An item inside an OBS scene (a placed source, group or nested scene). */
@Immutable
data class SceneItemInfo(
    val id: Int,
    val index: Int,
    val sourceName: String,
    val enabled: Boolean,
    val isGroup: Boolean,
    /** Input kind when the source is an input (null for scenes/groups). */
    val inputKind: String? = null,
)
