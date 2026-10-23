package com.scenedeck.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.MeterRefresh
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.components.studioSegmentedButtonColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.UiDensity

/** Callbacks for the density, meter refresh and audio allow-list settings. */
internal data class LayoutAndAudioActions(
    val onDensityChange: (UiDensity) -> Unit = {},
    val onMeterRefreshChange: (MeterRefresh) -> Unit = {},
    /** Row tap opens the sheet; inside the sheet the same slot loads the OBS input choices. */
    val onOpenAllowList: () -> Unit = {},
    val onAudioInputAllowedChange: (String, Boolean) -> Unit = { _, _ -> },
    val onClearAllowList: () -> Unit = {},
)

/** Compact / Comfortable segmented choice. */
@Composable
internal fun DensityChooser(density: UiDensity, onDensityChange: (UiDensity) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        UiDensity.entries.forEachIndexed { index, option ->
            SegmentedButton(
                colors = studioSegmentedButtonColors(),
                selected = density == option,
                onClick = { onDensityChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, UiDensity.entries.size),
            ) {
                Text(
                    when (option) {
                        UiDensity.COMFORTABLE -> stringResource(R.string.density_comfortable)
                        UiDensity.COMPACT -> stringResource(R.string.density_compact)
                    }
                )
            }
        }
    }
}

/** Meter refresh choice plus the allow-list entry row. */
@Composable
internal fun MixerSettingsPanel(settings: LayoutAndAudioSettings, actions: LayoutAndAudioActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.meter_refresh),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MeterRefresh.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    colors = studioSegmentedButtonColors(),
                    selected = settings.meterRefresh == option,
                    onClick = { actions.onMeterRefreshChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, MeterRefresh.entries.size),
                ) {
                    Text(meterRefreshLabel(option), maxLines = 1)
                }
            }
        }
        Text(
            meterRefreshDescription(settings.meterRefresh),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        AllowListRow(settings.audioAllowList.size, actions.onOpenAllowList)
    }
}

@Composable
private fun meterRefreshLabel(option: MeterRefresh): String =
    when (option) {
        MeterRefresh.SMOOTH -> stringResource(R.string.meter_refresh_smooth)
        MeterRefresh.BALANCED -> stringResource(R.string.meter_refresh_balanced)
        MeterRefresh.SAVER -> stringResource(R.string.meter_refresh_saver)
        MeterRefresh.OFF -> stringResource(R.string.meter_refresh_off)
    }

@Composable
private fun meterRefreshDescription(option: MeterRefresh): String =
    when (option) {
        MeterRefresh.SMOOTH -> stringResource(R.string.meter_refresh_smooth_desc)
        MeterRefresh.BALANCED -> stringResource(R.string.meter_refresh_balanced_desc)
        MeterRefresh.SAVER -> stringResource(R.string.meter_refresh_saver_desc)
        MeterRefresh.OFF -> stringResource(R.string.meter_refresh_off_desc)
    }

@Composable
private fun AllowListRow(allowedCount: Int, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StudioIconWell(SceneDeckIcons.Mixer, tone = StudioTone.SECONDARY)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.audio_allow_list),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (allowedCount == 0) stringResource(R.string.audio_allow_list_all)
                    else
                        pluralStringResource(
                            R.plurals.audio_allow_list_count,
                            allowedCount,
                            allowedCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.edit),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Checklist of OBS audio inputs; checking any limits the Mixer to the checked inputs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioAllowListSheet(
    allowList: Set<String>,
    choices: AudioInputChoices,
    actions: LayoutAndAudioActions,
    onDismiss: () -> Unit,
) {
    // Loads whenever the sheet appears, including when rememberSaveable reopens it after
    // recreation, so it never shows stale or empty choices.
    LaunchedEffect(Unit) { actions.onOpenAllowList() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AudioAllowListContent(allowList, choices, actions)
    }
}

@Composable
internal fun AudioAllowListContent(
    allowList: Set<String>,
    choices: AudioInputChoices,
    actions: LayoutAndAudioActions,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.audio_allow_list), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.audio_allow_list_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when {
            choices.loading ->
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            choices.names.isEmpty() ->
                Text(
                    stringResource(R.string.audio_allow_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else ->
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(choices.names, key = { it }) { name ->
                        AllowListChoice(name, name in allowList, actions.onAudioInputAllowedChange)
                    }
                }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = actions.onClearAllowList, enabled = allowList.isNotEmpty()) {
            Text(stringResource(R.string.audio_allow_list_clear))
        }
    }
}

@Composable
private fun AllowListChoice(name: String, checked: Boolean, onChange: (String, Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = checked,
            role = Role.Checkbox,
        ) {
            onChange(name, it)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

@PreviewLightDark
@Composable
private fun AudioAllowListPreview() {
    SceneDeckTheme {
        Surface {
            AudioAllowListContent(
                allowList = setOf("Mic/Aux"),
                choices =
                    AudioInputChoices(names = listOf("Desktop Audio", "Mic/Aux", "Music Bed")),
                actions = LayoutAndAudioActions(),
            )
        }
    }
}
