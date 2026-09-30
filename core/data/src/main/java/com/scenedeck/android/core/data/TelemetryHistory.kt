package com.scenedeck.android.core.data

/** One mapped 1 Hz telemetry sample kept in the rolling 2-minute window. */
data class TelemetrySample(
    val fps: Float,
    val renderTimeMs: Float,
    /** Dropped-frame % of the streaming output (skipped / total × 100). */
    val droppedPct: Float,
    /** Network congestion 0..100 %. */
    val congestionPct: Float,
    val bitrateKbps: Int,
    /** Render-lag frames skipped since the previous sample. */
    val renderSkippedDelta: Int,
    /** Output-lag frames skipped since the previous sample. */
    val outputSkippedDelta: Int,
    val renderSkippedTotal: Int,
    val outputSkippedTotal: Int,
)

/**
 * Maps one [Telemetry] snapshot into a [TelemetrySample]; per-sample skipped/missed frame counts
 * come from consecutive counter deltas (first sample = 0 deltas).
 */
fun Telemetry.toSample(previous: TelemetrySample?): TelemetrySample {
    val currentStats = stats
    val renderTotal = currentStats?.renderSkippedFrames ?: 0
    val outputTotal = currentStats?.outputSkippedFrames ?: 0
    val streamTotal = stream?.totalFrames ?: 0
    return TelemetrySample(
        fps = currentStats?.activeFps?.toFloat() ?: 0f,
        renderTimeMs = currentStats?.averageFrameRenderTimeMs?.toFloat() ?: 0f,
        droppedPct =
            if (streamTotal > 0) {
                (stream?.skippedFrames ?: 0) * 100f / streamTotal
            } else {
                0f
            },
        congestionPct = ((stream?.congestion ?: 0.0) * 100).toFloat(),
        bitrateKbps = bitrateKbps,
        renderSkippedDelta =
            (renderTotal - (previous?.renderSkippedTotal ?: renderTotal)).coerceAtLeast(0),
        outputSkippedDelta =
            (outputTotal - (previous?.outputSkippedTotal ?: outputTotal)).coerceAtLeast(0),
        renderSkippedTotal = renderTotal,
        outputSkippedTotal = outputTotal,
    )
}

/**
 * Rolling 2-minute window of 1 Hz samples (docs/FEATURE_SPEC.md §5). Owned by [StatsRepository] so
 * the window covers the entire connection session, not just the time a page is subscribed.
 */
class TelemetryHistory(val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "Telemetry history capacity must be positive" }
    }

    private val deque = ArrayDeque<TelemetrySample>(capacity)

    val size: Int
        get() = deque.size

    fun add(sample: TelemetrySample) {
        while (deque.size >= capacity) deque.removeFirst()
        deque.addLast(sample)
    }

    fun latest(): TelemetrySample? = deque.lastOrNull()

    fun toList(): List<TelemetrySample> = deque.toList()

    fun clear() = deque.clear()

    companion object {
        /** 2 minutes at 1 Hz. */
        const val DEFAULT_CAPACITY = 120
    }
}
