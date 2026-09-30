package com.scenedeck.android.core.designsystem.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.scenedeck.android.core.model.VolumeMeterReading

/**
 * Holder for one input's live meter reading. Read in the Canvas DRAW PHASE only — mutating
 * [reading] at meter cadence must never trigger recomposition.
 */
class MeterLevelsHolder {
    val reading: MutableState<VolumeMeterReading?> = mutableStateOf(null)
}

/**
 * Per-input map of [MeterLevelsHolder]s. Owned by ViewModels (one per screen), fed from
 * `ObsClient.volumeMeters`; read by [VolumeMeter] in draw phase.
 */
class MeterLevelsStore {
    private val holders = mutableMapOf<String, MeterLevelsHolder>()

    fun holder(inputName: String): MeterLevelsHolder =
        holders.getOrPut(inputName) { MeterLevelsHolder() }

    fun update(batch: List<VolumeMeterReading>) {
        batch.forEach { reading -> holder(reading.inputName).reading.value = reading }
    }
}
