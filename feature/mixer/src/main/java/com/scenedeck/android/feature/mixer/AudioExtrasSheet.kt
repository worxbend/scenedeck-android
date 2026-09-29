package com.scenedeck.android.feature.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.model.MonitorTypeKind

private const val MAX_SYNC_OFFSET_MS = 950

/** Current audio extras for one input (loaded when the sheet opens). Balance is OBS-domain 0..1, 0.5 = center. */
data class AudioExtras(
    val balance: Double = 0.5,
    val syncOffsetMs: Int = 0,
    val monitorType: MonitorTypeKind = MonitorTypeKind.NONE,
)

/** Per-strip audio extras sheet: balance, sync offset, monitor type (FEATURE_SPEC §8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioExtrasSheet(
    inputName: String,
    loadExtras: suspend () -> AudioExtras,
    onBalanceChange: (Double) -> Unit,
    onSyncOffsetChange: (Int) -> Unit,
    onMonitorTypeChange: (MonitorTypeKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var extras by remember { mutableStateOf<AudioExtras?>(null) }
    var balance by remember { mutableDoubleStateOf(0.5) }
    var syncOffset by remember { mutableIntStateOf(0) }
    var monitorType by remember { mutableStateOf(MonitorTypeKind.NONE) }

    LaunchedEffect(Unit) {
        loadExtras().let { loaded ->
            extras = loaded
            balance = loaded.balance
            syncOffset = loaded.syncOffsetMs
            monitorType = loaded.monitorType
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = inputName, style = MaterialTheme.typography.titleLarge)

            if (extras == null) {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Balance 0..1, 0.5 = center (OBS domain; debounced at the VM like the fader).
                Column {
                    val panPct = ((balance - 0.5) * 200).toInt()
                    Text(
                        text = when {
                            panPct == 0 -> "Balance: Center"
                            panPct < 0 -> "Balance: L ${-panPct}%"
                            else -> "Balance: R $panPct%"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    Slider(
                        value = balance.toFloat(),
                        onValueChange = {
                            balance = it.toDouble()
                            onBalanceChange(balance)
                        },
                        valueRange = 0f..1f,
                    )
                }

                // Sync offset stepper (ms, ±950 per OBS).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sync offset",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    StepperButton(label = "−50") {
                        syncOffset = (syncOffset - 50).coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
                        onSyncOffsetChange(syncOffset)
                    }
                    Text(
                        text = "$syncOffset ms",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    StepperButton(label = "+50") {
                        syncOffset = (syncOffset + 50).coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
                        onSyncOffsetChange(syncOffset)
                    }
                }

                // Monitor type.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        MonitorTypeKind.NONE to "Off",
                        MonitorTypeKind.MONITOR_ONLY to "Monitor",
                        MonitorTypeKind.MONITOR_AND_OUTPUT to "Mon+Out",
                    ).forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            selected = monitorType == type,
                            onClick = {
                                monitorType = type
                                onMonitorTypeChange(type)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
