package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.VolumeMeterReading
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.sample

/**
 * Mixer meter refresh behavior (FEATURE_SPEC §9). OBS pushes `InputVolumeMeters` at a fixed ~50 ms
 * cadence; slower settings keep only the latest batch per period so meters redraw less often.
 */
enum class MeterRefresh(
    /** Minimum time between applied meter batches; 0 applies every OBS batch. */
    val periodMs: Long
) {
    /** Every OBS batch (~20 Hz). */
    SMOOTH(0),

    /** Latest batch every 100 ms (~10 Hz). */
    BALANCED(100),

    /** Latest batch every 250 ms (~4 Hz), for battery or slow devices. */
    SAVER(250),

    /** Meters are not processed or drawn. */
    OFF(0);

    companion object {
        fun fromName(name: String?): MeterRefresh =
            entries.firstOrNull { it.name == name } ?: SMOOTH
    }
}

/** Applies [refresh] to a meter stream: pass-through, sampled to the latest batch, or nothing. */
@OptIn(FlowPreview::class)
fun Flow<List<VolumeMeterReading>>.withMeterRefresh(
    refresh: MeterRefresh
): Flow<List<VolumeMeterReading>> =
    when {
        refresh == MeterRefresh.OFF -> emptyFlow()
        refresh.periodMs == 0L -> this
        else -> sample(refresh.periodMs)
    }
