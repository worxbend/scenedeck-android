package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** OBS canvas video settings (`GetVideoSettings`). */
@Immutable
data class ObsVideoSettings(
    val fpsNumerator: Int,
    val fpsDenominator: Int,
    val baseWidth: Int,
    val baseHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
) {
    /** Canvas frame rate (e.g. 30000/1001 = 29.97), or null when OBS reports an invalid ratio. */
    val fps: Double?
        get() =
            if (fpsNumerator > 0 && fpsDenominator > 0) {
                fpsNumerator.toDouble() / fpsDenominator
            } else {
                null
            }
}
