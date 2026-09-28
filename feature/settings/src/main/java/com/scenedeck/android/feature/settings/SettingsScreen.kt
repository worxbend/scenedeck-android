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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.DarkMode
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
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Make the deck yours: theme family, appearance, motion and feedback.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        SectionLabel("Theme")
        ThemeSwitcher(current = currentTheme, onSelect = onThemeSelect)
        Spacer(Modifier.height(24.dp))

        SectionLabel("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DarkMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = darkMode == mode,
                    onClick = { onDarkModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DarkMode.entries.size),
                ) {
                    Text(
                        when (mode) {
                            DarkMode.SYSTEM -> "System"
                            DarkMode.LIGHT -> "Light"
                            DarkMode.DARK -> "Dark"
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel("Motion level")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MotionLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = motionLevel == level,
                    onClick = { onMotionLevelChange(level) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MotionLevel.entries.size),
                ) {
                    Text(
                        when (level) {
                            MotionLevel.FULL -> "Full"
                            MotionLevel.REDUCED -> "Reduced"
                            MotionLevel.OFF -> "Off"
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel("Feedback")
        ToggleRow(
            title = "Dynamic color",
            subtitle = "Material You colors from your wallpaper (Android 12+)",
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange,
        )
        ToggleRow(
            title = "Haptics",
            subtitle = "Tactile confirmation on deck presses and transport controls",
            checked = haptics,
            onCheckedChange = onHapticsChange,
        )

        SectionLabel("Display")
        ToggleRow(
            title = "Keep screen on",
            subtitle = "Prevent the display from sleeping while connected to OBS",
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
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
        SettingsScreen(
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
        )
    }
}
