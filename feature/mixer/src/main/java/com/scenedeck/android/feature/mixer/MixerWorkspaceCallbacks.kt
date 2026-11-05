package com.scenedeck.android.feature.mixer

import com.scenedeck.android.core.data.WorkspaceSession

internal fun MixerViewModel.callbacks(origin: WorkspaceSession) =
    MixerCallbacks(
        onModeChange = { setMode(origin, it) },
        onSelectScene = { selectScene(origin, it) },
        onGroupingChange = ::setGrouping,
        onSearchChange = ::setSearch,
        onVolumePreview = { name, value -> onVolumePreview(origin, name, value) },
        onVolumeCommit = { name, value -> onVolumeCommit(origin, name, value) },
        onToggleMute = { name, value -> toggleMute(origin, name, value) },
        onToggleLock = { name, value -> toggleLock(origin, name, value) },
        onMediaPlayPause = { mediaPlayPause(origin, it) },
        onMediaRestart = { mediaRestart(origin, it) },
    )
