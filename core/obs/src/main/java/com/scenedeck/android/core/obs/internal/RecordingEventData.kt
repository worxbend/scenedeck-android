package com.scenedeck.android.core.obs.internal

import kotlinx.serialization.Serializable

/** ktobs 0.5.0 requires a non-null path; OBS sends null except when recording stops. */
@Serializable
internal data class RecordingEventData(
    val outputActive: Boolean,
    val outputState: String,
    val outputPath: String? = null,
)
