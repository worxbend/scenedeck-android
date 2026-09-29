package com.scenedeck.android.core.model

/** Where an audio input sits in the OBS hierarchy (drives the mixer scope badge). */
enum class MixerScope {
    /** Global "special" input (Desktop/Mic). */
    GLOBAL,

    /** Direct item of the active program scene. */
    SCENE,

    /** Found by recursing into a nested scene source. */
    NESTED,

    /** Found inside a group item. */
    GROUP,
}
