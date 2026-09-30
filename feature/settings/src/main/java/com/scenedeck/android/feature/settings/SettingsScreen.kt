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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.components.studioSegmentedButtonColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.ThemeSwitcher
import com.scenedeck.android.core.designsystem.theme.families.displayName

/**
 * Settings (FEATURE_SPEC §9). The theme section hosts the design system's [ThemeSwitcher]; mode,
 * motion, dynamic color and haptics round out Settings v1 (full persistence arrives in M2).
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showThemes by rememberSaveable { mutableStateOf(false) }
    if (showThemes) {
        ModalBottomSheet(onDismissRequest = { showThemes = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(
                    stringResource(R.string.section_theme),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                ThemeSwitcher(
                    current = currentTheme,
                    onSelect = {
                        onThemeSelect(it)
                        showThemes = false
                    },
                )
            }
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        StudioPageHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
            icon = SceneDeckIcons.Settings,
        )
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.section_theme))
        Surface(
            onClick = { showThemes = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StudioIconWell(SceneIcon.SPARKLES.imageVector, tone = StudioTone.TERTIARY)
                Text(
                    currentTheme.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.change_theme),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_appearance))
        SettingsPanel {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DarkMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        colors = studioSegmentedButtonColors(),
                        selected = darkMode == mode,
                        onClick = { onDarkModeChange(mode) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DarkMode.entries.size,
                            ),
                    ) {
                        Text(
                            when (mode) {
                                DarkMode.SYSTEM -> stringResource(R.string.dark_mode_system)
                                DarkMode.LIGHT -> stringResource(R.string.dark_mode_light)
                                DarkMode.DARK -> stringResource(R.string.dark_mode_dark)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_motion))
        SettingsPanel {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MotionLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        colors = studioSegmentedButtonColors(),
                        selected = motionLevel == level,
                        onClick = { onMotionLevelChange(level) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = MotionLevel.entries.size,
                            ),
                    ) {
                        Text(
                            when (level) {
                                MotionLevel.FULL -> stringResource(R.string.motion_full)
                                MotionLevel.REDUCED -> stringResource(R.string.motion_reduced)
                                MotionLevel.OFF -> stringResource(R.string.motion_off)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.section_feedback))
        SettingsPanel {
            ToggleRow(
                title = stringResource(R.string.dynamic_color),
                icon = SceneIcon.SPARKLES.imageVector,
                tone = StudioTone.TERTIARY,
                subtitle = stringResource(R.string.dynamic_color_desc),
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
            ToggleRow(
                title = stringResource(R.string.haptics),
                icon = SceneIcon.BOLT.imageVector,
                tone = StudioTone.SECONDARY,
                subtitle = stringResource(R.string.haptics_desc),
                checked = haptics,
                onCheckedChange = onHapticsChange,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.section_display))
        SettingsPanel {
            ToggleRow(
                title = stringResource(R.string.keep_screen_on),
                icon = SceneIcon.MONITOR.imageVector,
                tone = StudioTone.PRIMARY,
                subtitle = stringResource(R.string.keep_screen_on_desc),
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.section_output_safety))
        SettingsPanel {
            ToggleRow(
                title = stringResource(R.string.confirm_start_stream),
                icon = SceneIcon.STREAM.imageVector,
                tone = StudioTone.PRIMARY,
                subtitle = stringResource(R.string.confirm_start_stream_desc),
                checked = outputSafety.confirmStartStream,
                onCheckedChange = onConfirmStartStreamChange,
            )
            ToggleRow(
                title = stringResource(R.string.confirm_stop_stream),
                icon = SceneIcon.STREAM.imageVector,
                tone = StudioTone.WARNING,
                subtitle = stringResource(R.string.confirm_stop_stream_desc),
                checked = outputSafety.confirmStopStream,
                onCheckedChange = onConfirmStopStreamChange,
            )
            ToggleRow(
                title = stringResource(R.string.confirm_start_record),
                icon = SceneIcon.RECORD.imageVector,
                tone = StudioTone.PRIMARY,
                subtitle = stringResource(R.string.confirm_start_record_desc),
                checked = outputSafety.confirmStartRecord,
                onCheckedChange = onConfirmStartRecordChange,
            )
            ToggleRow(
                title = stringResource(R.string.confirm_stop_record),
                icon = SceneIcon.RECORD.imageVector,
                tone = StudioTone.WARNING,
                subtitle = stringResource(R.string.confirm_stop_record_desc),
                checked = outputSafety.confirmStopRecord,
                onCheckedChange = onConfirmStopRecordChange,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    StudioSectionHeader(title = text)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: StudioTone,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudioIconWell(icon = icon, tone = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
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
