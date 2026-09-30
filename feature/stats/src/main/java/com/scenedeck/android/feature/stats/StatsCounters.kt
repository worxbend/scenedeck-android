package com.scenedeck.android.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.mono

/** Live resource readings stay above the fold; session totals follow the charts. */
@Composable
internal fun TelemetryOverview(uiState: StatsUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewReading(
                "Bitrate",
                "%,d".format(uiState.bitrateKbps),
                "kb/s",
                Modifier.weight(1.2f),
            )
            OverviewReading("CPU", "%.1f".format(uiState.cpuUsagePct), "%", Modifier.weight(1f))
            OverviewReading(
                "Memory",
                "%.0f".format(uiState.memoryUsageMb),
                "MB",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewReading(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.mono,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
        )
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CounterCards(uiState: StatsUiState) {
    val counters =
        listOf(
            "Rendered" to "%,d".format(uiState.renderTotalFrames),
            "Output frames" to "%,d".format(uiState.outputTotalFrames),
            "Render skipped" to "%,d".format(uiState.renderSkippedFrames),
            "Output skipped" to "%,d".format(uiState.outputSkippedFrames),
            "Streamed" to formatBytes(uiState.streamBytes),
            "Recorded" to formatBytes(uiState.recordBytes),
        )
    counters.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowItems.forEach { (label, value) ->
                CounterCard(
                    label = label,
                    value = value,
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CounterCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.mono,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
            )
        }
    }
}

@Composable
internal fun OutputStateChips(uiState: StatsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutputChip(
            label = if (uiState.streamActive) "STREAM LIVE" else "STREAM OFF",
            active = uiState.streamActive,
            activeColor = MaterialTheme.colorScheme.primary,
        )
        OutputChip(
            label =
                when {
                    uiState.recordActive && uiState.recordPaused -> "REC PAUSED"
                    uiState.recordActive -> "REC"
                    else -> "REC OFF"
                },
            active = uiState.recordActive,
            activeColor = SceneDeckTheme.colors.recording,
        )
    }
}

@Composable
private fun OutputChip(
    label: String,
    active: Boolean,
    activeColor: Color,
) {
    val container =
        if (active) {
            activeColor.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = if (active) activeColor else MaterialTheme.colorScheme.outline,
            ) {}
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "%d B".format(bytes)
    } else {
        "%.2f %s".format(value, units[unit])
    }
}
