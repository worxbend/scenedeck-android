package com.scenedeck.android.feature.stats

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.StatsRepository
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.data.TelemetrySample
import com.scenedeck.android.core.designsystem.components.TrendSamplesHolder
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Warn/crit gauge thresholds (docs/FEATURE_SPEC.md §5). */
object StatsThresholds {
    /** Dropped-frame % of the streaming output. */
    const val DROPPED_WARN_PCT = 1f
    const val DROPPED_CRIT_PCT = 5f
    const val DROPPED_MAX_PCT = 10f

    /** Network congestion %. */
    const val CONGESTION_WARN_PCT = 30f
    const val CONGESTION_CRIT_PCT = 60f
    const val CONGESTION_MAX_PCT = 100f

    /** FPS gauge: max = 60 fps stream target; falling direction (low FPS is bad). */
    const val FPS_MAX = 60f
    const val FPS_WARN = 54f // 90 % of target
    const val FPS_CRIT = 45f // 75 % of target

    /** Avg frame render time vs the 60 fps frame budget: warn 50 %, crit 90 %. */
    const val FRAME_BUDGET_MS = 1000f / 60f
    const val RENDER_WARN_MS = FRAME_BUDGET_MS * 0.5f
    const val RENDER_CRIT_MS = FRAME_BUDGET_MS * 0.9f
    const val RENDER_MAX_MS = FRAME_BUDGET_MS * 1.2f
}

data class StatsUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** Samples collected so far (0 → "collecting telemetry" state). */
    val sampleCount: Int = 0,
    val fps: Float = 0f,
    val renderTimeMs: Float = 0f,
    val droppedPct: Float = 0f,
    val congestionPct: Float = 0f,
    val cpuUsagePct: Double = 0.0,
    val memoryUsageMb: Double = 0.0,
    val bitrateKbps: Int = 0,
    val renderTotalFrames: Int = 0,
    val renderSkippedFrames: Int = 0,
    val outputTotalFrames: Int = 0,
    val outputSkippedFrames: Int = 0,
    val streamBytes: Long = 0,
    val recordBytes: Long = 0,
    val streamActive: Boolean = false,
    val recordActive: Boolean = false,
    val recordPaused: Boolean = false,
)

/**
 * Per-sample skipped/missed frame series for the bar visualization. Read in the Canvas DRAW PHASE
 * only (same contract as `MeterLevelsHolder`).
 */
class FrameDropSeriesHolder {
    val renderSkipped: MutableState<List<Float>> = mutableStateOf(emptyList())
    val outputSkipped: MutableState<List<Float>> = mutableStateOf(emptyList())
}

/**
 * Stats page state (docs/FEATURE_SPEC.md §5): renders the connection-scoped 120-sample ring buffer
 * owned by [StatsRepository] (`samples`), feeds the trend/drop draw-phase holders and exposes the
 * counter-card state.
 */
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    private val stats: StatsRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** FPS trend window (read by TrendChart in draw phase). */
    val fpsTrend = TrendSamplesHolder()

    /** Avg render time trend window. */
    val renderTrend = TrendSamplesHolder()

    /** Skipped/missed frame bars. */
    val frameDrops = FrameDropSeriesHolder()

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    val motionLevel: StateFlow<MotionLevel> =
        settings.settings
            .map {
                runCatching { MotionLevel.valueOf(it.motionLevel) }.getOrDefault(MotionLevel.FULL)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MotionLevel.FULL)

    init {
        viewModelScope.launch {
            combine(stats.telemetry, stats.samples) { telemetry, samples -> telemetry to samples }
                .collect { (telemetry, samples) ->
                    val connectedSamples =
                        if (telemetry.connection is ConnectionState.Ready) {
                            samples
                        } else {
                            emptyList()
                        }
                    updateHolders(connectedSamples)
                    _uiState.value =
                        if (telemetry.connection is ConnectionState.Ready) {
                            telemetry.toUiState(
                                connectedSamples.size,
                                connectedSamples.lastOrNull(),
                            )
                        } else {
                            StatsUiState(connection = telemetry.connection)
                        }
                }
        }
    }

    private fun updateHolders(samples: List<TelemetrySample>) {
        fpsTrend.samples.value = samples.map { it.fps }
        renderTrend.samples.value = samples.map { it.renderTimeMs }
        frameDrops.renderSkipped.value = samples.map { it.renderSkippedDelta.toFloat() }
        frameDrops.outputSkipped.value = samples.map { it.outputSkippedDelta.toFloat() }
    }

    private fun Telemetry.toUiState(sampleCount: Int, latest: TelemetrySample?) =
        StatsUiState(
            connection = connection,
            sampleCount = sampleCount,
            fps = latest?.fps ?: 0f,
            renderTimeMs = latest?.renderTimeMs ?: 0f,
            droppedPct = latest?.droppedPct ?: 0f,
            congestionPct = latest?.congestionPct ?: 0f,
            cpuUsagePct = stats?.cpuUsage ?: 0.0,
            memoryUsageMb = stats?.memoryUsageMb ?: 0.0,
            bitrateKbps = bitrateKbps,
            renderTotalFrames = stats?.renderTotalFrames ?: 0,
            renderSkippedFrames = stats?.renderSkippedFrames ?: 0,
            outputTotalFrames = stats?.outputTotalFrames ?: 0,
            outputSkippedFrames = stats?.outputSkippedFrames ?: 0,
            streamBytes = stream?.bytes ?: 0L,
            recordBytes = record?.bytes ?: 0L,
            streamActive = stream?.active == true,
            recordActive = record?.active == true,
            recordPaused = record?.paused == true,
        )
}
