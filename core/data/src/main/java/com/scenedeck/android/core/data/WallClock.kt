package com.scenedeck.android.core.data

/** Device calendar time, for human-readable saved timestamps; never used to judge freshness. */
fun interface WallClock {
    fun nowMs(): Long
}
