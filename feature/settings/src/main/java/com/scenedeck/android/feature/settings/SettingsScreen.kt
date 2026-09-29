package com.scenedeck.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.ThemeSwitcher

/**
 * Settings (FEATURE_SPEC §9). The theme section hosts the design system's
 * [ThemeSwitcher]; mode, motion, dynamic color and haptics round out Settings v1
 * (full persistence arrives in M2).
 */
@Composable
fun SettingsScreen(
    currentTheme: ThemeFamily,
    onThemeSelect: (ThemeFamily) -> Unit,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    motionLevel: MotionLevel,
    onMotionLevelChange: (MotionLevel) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    haptics: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val outputSafety by viewModel.outputSafety.collectAsStateWithLifecycle()
    SettingsContent(
        currentTheme = currentTheme,
        onThemeSelect = onThemeSelect,
        darkMode = darkMode,
        onDarkModeChange = onDarkModeChange,
        motionLevel = motionLevel,
        onMotionLevelChange = onMotionLevelChange,
        dynamicColor = dynamicColor,
        onDynamicColorChange = onDynamicColorChange,
        haptics = haptics,
        onHapticsChange = onHapticsChange,
        keepScreenOn = keepScreenOn,
        onKeepScreenOnChange = onKeepScreenOnChange,
        outputSafety = outputSafety,
        onConfirmStartStreamChange = viewModel::setConfirmStartStream,
        onConfirmStopStreamChange = viewModel::setConfirmStopStream,
        onConfirmStartRecordChange = viewModel::setConfirmStartRecord,
        onConfirmStopRecordChange = viewModel::setConfirmStopRecord,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    currentTheme: ThemeFamily,
    onThemeSelect: (ThemeFamily) -> Unit,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    motionLevel: MotionLevel,
    onMotionLevelChange: (MotionLevel) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    haptics: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    outputSafety: OutputSafety,
    onConfirmStartStreamChange: (Boolean) -> Unit,
    onConfirmStopStreamChange: (Boolean) -> Unit,
    onConfirmStartRecordChange: (Boolean) -> Unit,
    onConfirmStopRecordChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        SectionLabel(stringResource(R.string.section_theme))
        ThemeSwitcher(current = currentTheme, onSelect = onThemeSelect)
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_appearance))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DarkMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = darkMode == mode,
                    onClick = { onDarkModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DarkMode.entries.size),
                ) {
                    Text(
                        when (mode) {
                            DarkMode.SYSTEM -> stringResource(R.string.dark_mode_system)
                            DarkMode.LIGHT -> stringResource(R.string.dark_mode_light)
                            DarkMode.DARK -> stringResource(R.string.dark_mode_dark)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_motion))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MotionLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = motionLevel == level,
                    onClick = { onMotionLevelChange(level) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MotionLevel.entries.size),
                ) {
                    Text(
                        when (level) {
                            MotionLevel.FULL -> stringResource(R.string.motion_full)
                            MotionLevel.REDUCED -> stringResource(R.string.motion_reduced)
                            MotionLevel.OFF -> stringResource(R.string.motion_off)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_feedback))
        ToggleRow(
            title = stringResource(R.string.dynamic_color),
            subtitle = stringResource(R.string.dynamic_color_desc),
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange,
        )
        ToggleRow(
            title = stringResource(R.string.haptics),
            subtitle = stringResource(R.string.haptics_desc),
            checked = haptics,
            onCheckedChange = onHapticsChange,
        )

        SectionLabel(stringResource(R.string.section_display))
        ToggleRow(
            title = stringResource(R.string.keep_screen_on),
            subtitle = stringResource(R.string.keep_screen_on_desc),
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
        )
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_output_safety))
        ToggleRow(
            title = stringResource(R.string.confirm_start_stream),
            subtitle = stringResource(R.string.confirm_start_stream_desc),
            checked = outputSafety.confirmStartStream,
            onCheckedChange = onConfirmStartStreamChange,
        )
        ToggleRow(
            title = stringResource(R.string.confirm_stop_stream),
            subtitle = stringResource(R.string.confirm_stop_stream_desc),
            checked = outputSafety.confirmStopStream,
            onCheckedChange = onConfirmStopStreamChange,
        )
        ToggleRow(
            title = stringResource(R.string.confirm_start_record),
            subtitle = stringResource(R.string.confirm_start_record_desc),
            checked = outputSafety.confirmStartRecord,
            onCheckedChange = onConfirmStartRecordChange,
        )
        ToggleRow(
            title = stringResource(R.string.confirm_stop_record),
            subtitle = stringResource(R.string.confirm_stop_record_desc),
            checked = outputSafety.confirmStopRecord,
            onCheckedChange = onConfirmStopRecordChange,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    SceneDeckTheme {
        SettingsContent(
            currentTheme = ThemeFamily.SCENEDECK,
            onThemeSelect = {},
            darkMode = DarkMode.SYSTEM,
            onDarkModeChange = {},
            motionLevel = MotionLevel.FULL,
            onMotionLevelChange = {},
            dynamicColor = false,
            onDynamicColorChange = {},
            haptics = true,
            onHapticsChange = {},
            keepScreenOn = false,
            onKeepScreenOnChange = {},
            outputSafety = OutputSafety(),
            onConfirmStartStreamChange = {},
            onConfirmStopStreamChange = {},
            onConfirmStartRecordChange = {},
            onConfirmStopRecordChange = {},
        )
    }
}
