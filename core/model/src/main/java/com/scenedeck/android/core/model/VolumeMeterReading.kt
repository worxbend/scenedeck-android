package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/**
 * Per-channel levels of one `InputVolumeMeters` sample. All values are linear
 * multipliers (OBS `inputLevelsMul` triples: magnitude / peak / inputPeak).
 */
@Immutable
data class ChannelLevels(
    val magnitudeMul: Float,
    val peakMul: Float,
    val inputPeakMul: Float,
)

/** One input's meter reading within an `InputVolumeMeters` batch. */
@Immutable
data class VolumeMeterReading(
    val inputName: String,
    val channels: List<ChannelLevels>,
)
