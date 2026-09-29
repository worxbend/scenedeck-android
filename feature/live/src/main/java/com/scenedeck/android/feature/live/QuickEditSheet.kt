package com.scenedeck.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector

internal fun argbLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

/** Fixed accent palette offered in quick-edit (argb stored in the registry). */
internal val AccentPalette: List<Color> = listOf(
    Color(0xFFEF5350),
    Color(0xFFFF9800),
    Color(0xFFFDD835),
    Color(0xFF66BB6A),
    Color(0xFF26A69A),
    Color(0xFF42A5F5),
    Color(0xFF7E57C2),
    Color(0xFFEC407A),
)

/** Long-press quick actions: deck role, accent color, icon (FEATURE_SPEC §2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEditSheet(
    scene: SceneCardState,
    onDismiss: () -> Unit,
    onSave: (primary: Boolean, accentArgb: Long?, iconName: String?) -> Unit,
) {
    var primary by rememberSaveable { mutableStateOf(scene.role == SceneRole.PRIMARY) }
    var accentArgb by rememberSaveable { mutableStateOf(scene.accentColorArgb) }
    var iconName by rememberSaveable { mutableStateOf(scene.iconName) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = scene.name, style = MaterialTheme.typography.titleLarge)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Show on deck", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Primary scenes appear on the Live page",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = primary, onCheckedChange = { primary = it })
            }

            Text(text = "Accent color", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentSwatch(
                    color = null,
                    selected = accentArgb == null,
                    onClick = { accentArgb = null },
                )
                AccentPalette.forEach { color ->
                    AccentSwatch(
                        color = color,
                        selected = accentArgb == argbLong(color),
                        onClick = { accentArgb = argbLong(color) },
                    )
                }
            }

            Text(text = "Icon", style = MaterialTheme.typography.labelLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SceneIcon.entries.toList(), key = { it.name }) { icon ->
                    val selected = iconName == icon.name ||
                        (iconName == null && icon == SceneIcon.SCENES)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable { iconName = icon.name }
                            .padding(6.dp),
                    ) {
                        Icon(
                            imageVector = icon.imageVector,
                            contentDescription = icon.name,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(primary, accentArgb, iconName) }) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    if (color == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(2.dp, borderColor, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "–", style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Spacer(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, borderColor, CircleShape)
                .clickable(onClick = onClick),
        )
    }
}
