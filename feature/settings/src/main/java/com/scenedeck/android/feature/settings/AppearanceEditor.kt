package com.scenedeck.android.feature.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.studioFilterChipColors
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.ThemePreview
import com.scenedeck.android.core.designsystem.theme.ThemeSwitcher
import com.scenedeck.android.core.designsystem.theme.families.displayName

@Composable
internal fun AppearanceOverview(value: AppearanceSettings?, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        enabled = value != null,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    value?.let {
                        if (it.wallpaper && Build.VERSION.SDK_INT >= 31) "Wallpaper colors"
                        else it.family.displayName
                    } ?: "Loading appearance…",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    value?.let {
                        "${it.accent.label} · ${if (it.trueBlack) "True black" else "Tonal surfaces"}"
                    } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.customize_appearance),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceEditor(
    state: AppearanceState,
    onEdit: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val draft = state.draft ?: return
    val saving by rememberUpdatedState(state.saving)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { !saving || it != SheetValue.Hidden },
            ),
    ) {
        AppearanceEditorContent(draft, state.saving, state.error, onEdit, onSave, onClose)
    }
}

@Composable
internal fun AppearanceEditorContent(
    draft: AppearanceSettings,
    saving: Boolean,
    error: String?,
    onEdit: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var palettes by rememberSaveable { mutableStateOf(false) }
    BackHandler(palettes && !saving) { palettes = false }
    Column(
        Modifier.heightIn(max = 900.dp).padding(horizontal = SceneDeckTheme.spacing.pageGutter)
    ) {
        StudioSectionHeader(
            stringResource(if (palettes) R.string.choose_palette else R.string.customize_appearance)
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (palettes) {
                TextButton(onClick = { palettes = false }) {
                    Text(stringResource(R.string.back_to_appearance))
                }
                ThemeSwitcher(
                    draft.family,
                    { family ->
                        if (!saving) {
                            onEdit { it.copy(family = family, wallpaper = false) }
                            palettes = false
                        }
                    },
                )
            } else {
                AppearanceChoices(draft, saving, onEdit) { palettes = true }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.padding(bottom = 4.dp))
        }
        AppearanceFooter(saving, onSave, onClose) { onEdit { AppearanceSettings() } }
    }
}

@Composable
private fun AppearanceFooter(
    saving: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onReset: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, enabled = !saving) {
            Text(
                stringResource(
                    if (saving) R.string.saving_appearance else R.string.apply_appearance
                )
            )
        }
        TextButton(onClick = onClose, enabled = !saving) {
            Text(stringResource(R.string.cancel_appearance))
        }
        TextButton(onClick = onReset, enabled = !saving) {
            Text(stringResource(R.string.reset_appearance))
        }
    }
}

@Composable
private fun AppearanceChoices(
    draft: AppearanceSettings,
    saving: Boolean,
    onEdit: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onPalettes: () -> Unit,
) {
    val dark = draft.isDark(isSystemInDarkTheme())
    val highContrast =
        draft.family == ThemeFamily.HIGH_CONTRAST &&
            !(draft.wallpaper && Build.VERSION.SDK_INT >= 31)
    ThemePreview(draft.family, dark, draft.wallpaper, draft.accent, draft.trueBlack)
    Text(
        stringResource(R.string.appearance_preview_help),
        style = MaterialTheme.typography.bodySmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DarkMode.entries.forEach { mode ->
            FilterChip(
                selected = draft.mode == mode,
                onClick = { onEdit { it.copy(mode = mode) } },
                enabled = !saving,
                label = { Text(mode.label()) },
                colors = studioFilterChipColors(),
            )
        }
    }
    TextButton(onClick = onPalettes, enabled = !saving) {
        Text("${stringResource(R.string.choose_palette)} · ${draft.family.displayName}")
    }
    AppearanceToggle(
        stringResource(R.string.dynamic_color),
        stringResource(R.string.wallpaper_help),
        draft.wallpaper,
        !saving && Build.VERSION.SDK_INT >= 31,
    ) { value ->
        onEdit { it.copy(wallpaper = value) }
    }
    AccentChoices(draft, saving || highContrast, onEdit)
    if (highContrast)
        Text(
            stringResource(R.string.high_contrast_preserved),
            style = MaterialTheme.typography.bodySmall,
        )
    AppearanceToggle(
        stringResource(R.string.true_black),
        stringResource(R.string.true_black_help),
        draft.trueBlack,
        !saving && !highContrast,
    ) { value ->
        onEdit { it.copy(trueBlack = value) }
    }
}

@Composable
private fun AccentChoices(
    draft: AppearanceSettings,
    disabled: Boolean,
    onEdit: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
) {
    StudioSectionHeader(stringResource(R.string.accent_color))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeAccent.entries.forEach { accent ->
            FilterChip(
                selected = draft.accent == accent,
                onClick = { onEdit { it.copy(accent = accent) } },
                enabled = !disabled,
                label = { Text(accent.label) },
                colors = studioFilterChipColors(),
            )
        }
    }
}

@Composable
private fun AppearanceToggle(
    title: String,
    help: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(help, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked,
            onChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun DarkMode.label(): String =
    stringResource(
        when (this) {
            DarkMode.SYSTEM -> R.string.dark_mode_system
            DarkMode.LIGHT -> R.string.dark_mode_light
            DarkMode.DARK -> R.string.dark_mode_dark
        }
    )
