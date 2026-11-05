package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** Advertised optional operations. Availability does not guarantee recording-format support. */
@Immutable
data class ObsCapabilities(
    val pauseRecording: Boolean = false,
    val resumeRecording: Boolean = false,
    val splitRecording: Boolean = false,
    val recordingChapter: Boolean = false,
    val mediaSeek: Boolean = false,
    val controls: Set<ObsControl> = emptySet(),
)
