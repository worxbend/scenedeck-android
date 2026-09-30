package com.scenedeck.android.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.GaugeDirection
import com.scenedeck.android.core.designsystem.components.GaugeZone
import com.scenedeck.android.core.designsystem.components.StatGauge
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.TrendChart
import com.scenedeck.android.core.designsystem.components.TrendSamplesHolder
import com.scenedeck.android.core.designsystem.components.classifyGaugeZone
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState

/**
 * Stats page — live stream telemetry dashboard (FEATURE_SPEC §5, milestone M5): threshold gauges,
 * 2-minute trend charts, per-sample dropped-frame bars and counter cards, refreshed at 1 Hz from
 * the `StatsRepository` poll loop.
 */
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val motionLevel by viewModel.motionLevel.collectAsStateWithLifecycle()

    when (val connection = uiState.connection) {
        is ConnectionState.Ready ->
            StatsContent(
                uiState = uiState,
                fpsTrend = viewModel.fpsTrend,
                renderTrend = viewModel.renderTrend,
                frameDrops = viewModel.frameDrops,
                motionLevel = motionLevel,
                modifier = modifier,
            )

        else ->
            DisconnectedPlaceholder(
                connectionState = connection,
                onConnect = onNavigateToConnections,
            )
    }
}

@Composable
internal fun StatsContent(
    uiState: StatsUiState,
    fpsTrend: TrendSamplesHolder,
    renderTrend: TrendSamplesHolder,
    frameDrops: FrameDropSeriesHolder,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        StudioPageHeader("Studio health", "Real-time performance & outputs", SceneDeckIcons.Stats)
        Spacer(Modifier.height(16.dp))

        if (uiState.sampleCount == 0) {
            CollectingTelemetry()
        } else {
            HealthSummary(uiState)
            Spacer(Modifier.height(12.dp))
            OutputStateChips(uiState)
            Spacer(Modifier.height(16.dp))
            TelemetryOverview(uiState)
            Spacer(Modifier.height(12.dp))
            GaugeRow(uiState)
            Spacer(Modifier.height(16.dp))
            StudioSectionHeader("Performance · last 2 minutes")
            Spacer(Modifier.height(12.dp))
            TrendCard("Frame rate") {
                TrendChart(
                    samplesHolder = fpsTrend,
                    motionLevel = motionLevel,
                    modifier =
                        Modifier.fillMaxWidth().height(100.dp).semantics {
                            contentDescription =
                                "FPS trend chart, current %.1f fps".format(uiState.fps)
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            TrendCard("Render time · ms") {
                TrendChart(
                    samplesHolder = renderTrend,
                    motionLevel = motionLevel,
                    modifier =
                        Modifier.fillMaxWidth().height(100.dp).semantics {
                            contentDescription =
                                "Render time trend chart, current %.1f ms"
                                    .format(uiState.renderTimeMs)
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            TrendCard("Skipped / missed frames per sample") {
                FrameDropBars(
                    frameDrops = frameDrops,
                    modifier =
                        Modifier.fillMaxWidth().height(72.dp).semantics {
                            contentDescription =
                                "Skipped frames per sample, ${uiState.renderSkippedFrames} render and " +
                                    "${uiState.outputSkippedFrames} output skipped in total"
                        },
                )
                Spacer(Modifier.height(8.dp))
                FrameDropLegend()
            }
            Spacer(Modifier.height(24.dp))
            Text("Session totals", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            CounterCards(uiState)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CollectingTelemetry() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Collecting telemetry…",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Gauges and charts fill in after the first 1 Hz sample.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GaugeRow(uiState: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugePanel(Modifier.weight(1f)) {
                StatGauge(
                    value = uiState.fps,
                    minValue = 0f,
                    maxValue = StatsThresholds.FPS_MAX,
                    warnThreshold = StatsThresholds.FPS_WARN,
                    critThreshold = StatsThresholds.FPS_CRIT,
                    label = "Frame rate",
                    unit = "fps",
                    direction = GaugeDirection.FALLING,
                    valueText = "%.1f".format(uiState.fps),
                    modifier = Modifier.width(104.dp),
                )
            }
            GaugePanel(Modifier.weight(1f)) {
                StatGauge(
                    value = uiState.renderTimeMs,
                    minValue = 0f,
                    maxValue = StatsThresholds.RENDER_MAX_MS,
                    warnThreshold = StatsThresholds.RENDER_WARN_MS,
                    critThreshold = StatsThresholds.RENDER_CRIT_MS,
                    label = "Render time",
                    unit = "ms",
                    valueText = "%.1f".format(uiState.renderTimeMs),
                    modifier = Modifier.width(104.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugePanel(Modifier.weight(1f)) {
                StatGauge(
                    value = uiState.droppedPct,
                    minValue = 0f,
                    maxValue = StatsThresholds.DROPPED_MAX_PCT,
                    warnThreshold = StatsThresholds.DROPPED_WARN_PCT,
                    critThreshold = StatsThresholds.DROPPED_CRIT_PCT,
                    label = "Dropped frames",
                    unit = "%",
                    valueText = "%.1f".format(uiState.droppedPct),
                    modifier = Modifier.width(104.dp),
                )
            }
            GaugePanel(Modifier.weight(1f)) {
                StatGauge(
                    value = uiState.congestionPct,
                    minValue = 0f,
                    maxValue = StatsThresholds.CONGESTION_MAX_PCT,
                    warnThreshold = StatsThresholds.CONGESTION_WARN_PCT,
                    critThreshold = StatsThresholds.CONGESTION_CRIT_PCT,
                    label = "Congestion",
                    unit = "%",
                    valueText = "%.0f".format(uiState.congestionPct),
                    modifier = Modifier.width(104.dp),
                )
            }
        }
    }
}

@Composable
private fun GaugePanel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * Shared gauge classification keeps the summary and individual readings consistent at boundaries.
 */
internal fun studioHealthZone(state: StatsUiState): GaugeZone =
    listOf(
            classifyGaugeZone(
                state.fps,
                StatsThresholds.FPS_WARN,
                StatsThresholds.FPS_CRIT,
                GaugeDirection.FALLING,
            ),
            classifyGaugeZone(
                state.renderTimeMs,
                StatsThresholds.RENDER_WARN_MS,
                StatsThresholds.RENDER_CRIT_MS,
            ),
            classifyGaugeZone(
                state.droppedPct,
                StatsThresholds.DROPPED_WARN_PCT,
                StatsThresholds.DROPPED_CRIT_PCT,
            ),
            classifyGaugeZone(
                state.congestionPct,
                StatsThresholds.CONGESTION_WARN_PCT,
                StatsThresholds.CONGESTION_CRIT_PCT,
            ),
        )
        .maxBy { it.ordinal }

@Composable
private fun HealthSummary(state: StatsUiState) {
    val colors = SceneDeckTheme.colors
    val zone = studioHealthZone(state)
    val critical = zone == GaugeZone.CRITICAL
    val warning = zone == GaugeZone.WARNING
    val accent =
        when {
            critical -> colors.meterRed
            warning -> colors.warning
            else -> colors.preview
        }
    Surface(shape = MaterialTheme.shapes.large, color = accent.copy(alpha = 0.10f)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(SceneIcon.STATS.imageVector, contentDescription = null, tint = accent)
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        critical -> "Studio needs attention"
                        warning -> "Performance warning"
                        else -> "Studio running smoothly"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
                Text(
                    "${state.sampleCount} samples · updated every second",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Per-sample skipped/missed frame bars: amber = render lag, red = output lag, stacked per 1 Hz
 * sample and right-aligned in the 2-minute window. Reads [frameDrops] in the DRAW PHASE only
 * (MeterLevelsHolder pattern).
 */
@Composable
private fun FrameDropBars(
    frameDrops: FrameDropSeriesHolder,
    modifier: Modifier = Modifier,
    windowSize: Int = com.scenedeck.android.core.data.TelemetryHistory.DEFAULT_CAPACITY,
) {
    val colors = SceneDeckTheme.colors
    val renderColor = colors.warning
    val outputColor = colors.meterRed
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth()) {
        val render = frameDrops.renderSkipped.value
        val output = frameDrops.outputSkipped.value
        drawLine(
            color = trackColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        if (render.isEmpty() && output.isEmpty()) return@Canvas

        val window = windowSize.coerceAtLeast(2)
        val sampleCount = maxOf(render.size, output.size)
        val peak =
            (0 until sampleCount)
                .maxOf {
                    (render.getOrNull(it) ?: 0f) + (output.getOrNull(it) ?: 0f)
                }
                .coerceAtLeast(1f)
        val barSlot = size.width / window
        val barWidth = (barSlot * 0.7f).coerceAtLeast(1f)
        val offset = window - sampleCount

        repeat(sampleCount) { index ->
            val renderValue = render.getOrNull(index) ?: 0f
            val outputValue = output.getOrNull(index) ?: 0f
            val x = (offset + index) * barSlot + (barSlot - barWidth) / 2
            val renderHeight = size.height * (renderValue / peak)
            val outputHeight = size.height * (outputValue / peak)
            if (renderHeight > 0f) {
                drawRect(
                    color = renderColor,
                    topLeft = Offset(x, size.height - renderHeight),
                    size = Size(barWidth, renderHeight),
                )
            }
            if (outputHeight > 0f) {
                drawRect(
                    color = outputColor,
                    topLeft = Offset(x, size.height - renderHeight - outputHeight),
                    size = Size(barWidth, outputHeight),
                )
            }
        }
    }
}
