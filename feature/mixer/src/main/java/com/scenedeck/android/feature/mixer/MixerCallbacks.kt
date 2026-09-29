package com.scenedeck.android.feature.mixer

/** All user actions from the mixer content (lambda bag keeps content VM-free). */
data class MixerCallbacks(
    val onModeChange: (MixerMode) -> Unit = {},
    val onSelectScene: (String) -> Unit = {},
    val onGroupingChange: (MixerGrouping) -> Unit = {},
    val onSearchChange: (String) -> Unit = {},
    val onVolumePreview: (String, Double) -> Unit = { _, _ -> },
    val onVolumeCommit: (String, Double) -> Unit = { _, _ -> },
    val onToggleMute: (String, Boolean) -> Unit = { _, _ -> },
    val onToggleLock: (String, Boolean) -> Unit = { _, _ -> },
    val onMediaPlayPause: (String) -> Unit = {},
    val onMediaRestart: (String) -> Unit = {},
)

