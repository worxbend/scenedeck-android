package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** An audio-capable input in OBS. */
@Immutable
data class InputInfo(
    val name: String,
    val kind: String? = null,
    val muted: Boolean? = null,
    /** Volume as a linear multiplier (0..~1+). dB conversion happens in the mixer. */
    val volumeMul: Float? = null,
)

/** Global "special" audio inputs (desktop/mic channels) from `GetSpecialInputs`. */
@Immutable
data class SpecialInputs(
    val desktop1: String? = null,
    val desktop2: String? = null,
    val mic1: String? = null,
    val mic2: String? = null,
    val mic3: String? = null,
    val mic4: String? = null,
) {
    /** All non-null special input names, OBS order (desktop first, then mics). */
    val names: List<String>
        get() = listOfNotNull(desktop1, desktop2, mic1, mic2, mic3, mic4)
}
