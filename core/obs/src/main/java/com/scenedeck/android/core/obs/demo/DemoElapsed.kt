package com.scenedeck.android.core.obs.demo

/** A local simulated output timer. Recording pause never advances the reported duration. */
internal class DemoElapsed(private val clock: () -> Long) {
    var active = false
        private set

    var paused = false
        private set

    private var accumulated = 0L
    private var since = clock()

    fun duration(): Long =
        accumulated + if (active && !paused) (clock() - since).coerceAtLeast(0) else 0

    fun start() {
        if (active) return
        active = true
        paused = false
        accumulated = 0
        since = clock()
    }

    fun stop() {
        accumulated = duration()
        active = false
        paused = false
    }

    fun pause(value: Boolean) {
        demoRequire(active)
        if (paused == value) return
        accumulated = duration()
        since = clock()
        paused = value
    }
}

internal fun demoTimecode(durationMs: Long): String {
    val seconds = durationMs / 1_000
    return "${(seconds / 3_600).toString().padStart(2, '0')}:" +
        "${(seconds / 60 % 60).toString().padStart(2, '0')}:" +
        (seconds % 60).toString().padStart(2, '0')
}
