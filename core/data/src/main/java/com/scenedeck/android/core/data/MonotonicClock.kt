package com.scenedeck.android.core.data

/** Process-local elapsed time including device sleep. Never use for persisted timestamps. */
fun interface MonotonicClock {
    fun nowMs(): Long
}
