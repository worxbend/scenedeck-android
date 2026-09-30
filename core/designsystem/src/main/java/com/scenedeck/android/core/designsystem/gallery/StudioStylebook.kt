package com.scenedeck.android.core.designsystem.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.components.studioFilterChipColors
import com.scenedeck.android.core.designsystem.components.studioSegmentedButtonColors
import com.scenedeck.android.core.designsystem.components.studioTextFieldColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.preview.DesignSystemPreview
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/** Living reference: shared chrome, controls, input surfaces and tile states. */
@Composable
fun StudioStylebook(modifier: Modifier = Modifier) {
    var enabled by remember { mutableStateOf(true) }
    var selection by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("192.168.1.200") }
    Surface(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioPageHeader("SceneDeck", "Studio stylebook · components", SceneDeckIcons.Scenes)
            StudioSectionHeader("Icon tones")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioTone.entries.forEach { StudioIconWell(SceneDeckIcons.Settings, tone = it) }
            }
            StudioSectionHeader("Actions & selection")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Connect") }
                FilledTonalButton(onClick = {}) { Text("Edit profile") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Scenes", "Mixer", "Stats").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selection == index,
                        onClick = { selection = index },
                        colors = studioSegmentedButtonColors(),
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) {
                        Text(label)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("All scenes") },
                    colors = studioFilterChipColors(),
                    border = null,
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Deck") },
                    colors = studioFilterChipColors(),
                    border = null,
                )
                Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            StudioSectionHeader("Connection inputs")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("OBS host") },
                leadingIcon = { Icon(SceneDeckIcons.Connections, null) },
                singleLine = true,
                colors = studioTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            StudioSectionHeader("Scene tiles")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SceneCard(
                    "Camera 1",
                    SceneDeckIcons.Scenes,
                    false,
                    Modifier.weight(1f),
                    sceneNumber = 1,
                )
                SceneCard(
                    "Starting soon",
                    SceneDeckIcons.Scenes,
                    true,
                    Modifier.weight(1f),
                    sceneNumber = 2,
                )
            }
        }
    }
}

@DesignSystemPreview
@Composable
private fun StudioStylebookPreview() {
    SceneDeckTheme { StudioStylebook() }
}
