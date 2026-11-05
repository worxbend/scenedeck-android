package com.scenedeck.android.feature.preflight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.MAX_MANUAL_CHECKS
import com.scenedeck.android.core.data.MAX_MANUAL_CHECK_LABEL
import com.scenedeck.android.core.data.MAX_PREFLIGHT_SCENES
import com.scenedeck.android.core.data.ManualPreflightCheck
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import java.util.UUID

@Composable
internal fun PreflightMicrophoneField(
    value: String,
    microphones: List<String>,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value,
        onChange,
        label = { Text("Microphone OBS input name") },
        supportingText = { Text("Use the literal OBS name. Blank leaves this check unknown.") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled && microphones.isNotEmpty()) {
            Text("Choose detected input")
        }
        DropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }) {
            microphones.distinct().forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun RequiredScenePicker(
    scenes: List<SceneCardState>,
    required: List<String>,
    enabled: Boolean,
    onChange: (List<String>) -> Unit,
) {
    val known = scenes.associateBy { it.name }
    val rows = (required + scenes.map { it.name }).distinct()
    StudioSectionHeader("Required scenes · ${required.size}/$MAX_PREFLIGHT_SCENES")
    LazyColumn(Modifier.heightIn(max = 280.dp)) {
        items(rows, key = { it }) { name ->
            val selected = name in required
            val canChange = enabled && (selected || required.size < MAX_PREFLIGHT_SCENES)
            ListItem(
                headlineContent = { Text(known[name]?.displayName ?: name) },
                supportingContent = {
                    Text(if (name in known) "OBS · $name" else "Unavailable in OBS · $name")
                },
                leadingContent = { Checkbox(selected, null, enabled = canChange) },
                modifier =
                    Modifier.toggleable(selected, enabled = canChange, role = Role.Checkbox) {
                        onChange(if (it) required + name else required - name)
                    },
            )
        }
    }
}

@Composable
internal fun ManualCheckEditor(
    checks: List<ManualPreflightCheck>,
    enabled: Boolean,
    onChange: (List<ManualPreflightCheck>) -> Unit,
) {
    StudioSectionHeader("Manual checks · ${checks.size}/$MAX_MANUAL_CHECKS")
    checks.forEach { item ->
        key(item.id) {
            Column {
                OutlinedTextField(
                    item.label,
                    { label ->
                        onChange(
                            checks.map { if (it.id == item.id) it.copy(label = label) else it }
                        )
                    },
                    label = { Text("Checklist item") },
                    supportingText = {
                        Text(
                            "${item.label.trim().length}/$MAX_MANUAL_CHECK_LABEL · " +
                                "changing wording clears its confirmation"
                        )
                    },
                    isError =
                        item.label.trim().isEmpty() ||
                            item.label.trim().length > MAX_MANUAL_CHECK_LABEL,
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(
                        onClick = { onChange(checks.filterNot { it.id == item.id }) },
                        enabled = enabled,
                    ) {
                        Text("Remove item")
                    }
                }
            }
        }
    }
    TextButton(
        onClick = { onChange(checks + ManualPreflightCheck(UUID.randomUUID().toString(), "")) },
        enabled = enabled && checks.size < MAX_MANUAL_CHECKS,
    ) {
        Text("Add manual check")
    }
}
