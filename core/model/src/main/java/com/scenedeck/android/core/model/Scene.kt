package com.scenedeck.android.core.model

/** A scene as reported by OBS. Curation metadata lives in the registry, never in OBS. */
data class Scene(
    val name: String,
    val index: Int,
)
