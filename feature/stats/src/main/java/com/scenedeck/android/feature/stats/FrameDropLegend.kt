package com.scenedeck.android.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Stats page — live stream telemetry dashboard (FEATURE_SPEC §5, milestone M5): threshold gauges,
 * 2-minute trend charts, per-sample dropped-frame bars and counter cards, refreshed at 1 Hz from
 * the `StatsRepository` poll loop.
 */
@Composable
internal fun FrameDropLegend() {
    val colors = SceneDeckTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendEntry(color = colors.warning, label = "Render lag")
        LegendEntry(color = colors.meterRed, label = "Output lag")
    }
}

@Composable
private fun LegendEntry(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = color,
        ) {}
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
