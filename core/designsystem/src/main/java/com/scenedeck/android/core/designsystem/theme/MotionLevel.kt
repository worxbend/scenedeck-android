package com.scenedeck.android.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme

/** User-configurable motion level (settings; also honor system animator scale). */
enum class MotionLevel {
    /** Expressive springs everywhere (default). */
    FULL,

    /** Standard Material motion; no tally pulse, crossfade instead of morphs. */
    REDUCED,

    /** No animation: every spec is a snap. */
    OFF,
}

private val StaticMotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/** Maps a user [MotionLevel] onto a Material [MotionScheme] (docs/DESIGN_SYSTEM.md §8). */
fun motionSchemeFor(level: MotionLevel): MotionScheme = when (level) {
    MotionLevel.FULL -> MotionScheme.expressive()
    MotionLevel.REDUCED -> MotionScheme.standard()
    MotionLevel.OFF -> StaticMotionScheme
}
