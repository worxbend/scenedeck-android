package com.scenedeck.android.core.obs.internal.protocol

import com.scenedeck.android.core.model.ChannelLevels
import com.scenedeck.android.core.model.VolumeMeterReading
import kotlinx.serialization.Serializable

/**
 * Our own `InputVolumeMeters` model. ktobs 0.5.0's typed variant cannot decode the real payload
 * (its `Input` requires `inputKind`, which meters don't carry), so we decode
 * `EventOpCode.eventData` ourselves (see core/obs/README.md, spike item b).
 */
@Serializable
internal data class InputVolumeMetersPayload(val inputs: List<MeterInput> = emptyList())

@Serializable
internal data class MeterInput(
    val inputName: String,
    /** Per-channel [magnitude, peak, inputPeak] linear multipliers. */
    val inputLevelsMul: List<List<Float>> = emptyList(),
)

internal fun InputVolumeMetersPayload.toDomain(): List<VolumeMeterReading> = inputs.map { input ->
    VolumeMeterReading(
        inputName = input.inputName,
        channels =
            input.inputLevelsMul.mapNotNull { triple ->
                if (triple.size >= LEVELS_PER_CHANNEL) {
                    ChannelLevels(
                        magnitudeMul = triple[0],
                        peakMul = triple[1],
                        inputPeakMul = triple[2],
                    )
                } else {
                    null
                }
            },
    )
}

private const val LEVELS_PER_CHANNEL = 3
