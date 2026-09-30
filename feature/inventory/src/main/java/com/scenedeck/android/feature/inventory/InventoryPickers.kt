package com.scenedeck.android.feature.inventory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.components.SceneAccentPalette
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector

/** Accent picker: palette dots + "none", used by the inventory accent sheet. */
@Composable
internal fun AccentPickerContent(current: Long?, onPick: (Long?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Accent color", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // None.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (current == null) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                            CircleShape,
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onPick(null) },
            ) {
                Text(text = "–", style = MaterialTheme.typography.labelMedium)
            }
            SceneAccentPalette.forEach { color ->
                val argb = argbToLong(color)
                androidx.compose.foundation.layout.Spacer(
                    modifier =
                        Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                2.dp,
                                if (current == argb) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { onPick(argb) }
                )
            }
        }
    }
}

/** Icon picker: SceneIcon catalogue grid, used by the inventory icon sheet. */
@Composable
internal fun IconPickerContent(current: String?, onPick: (String?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Scene icon", style = MaterialTheme.typography.titleLarge)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SceneIcon.entries.toList(), key = { it.name }) { icon ->
                val selected = current == icon.name || (current == null && icon == SceneIcon.SCENES)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.clip(MaterialTheme.shapes.medium)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onPick(icon.name) }
                            .padding(6.dp),
                ) {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = icon.name,
                        tint =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/** Six-dot drag grip used on inventory rows. */
@Composable
internal fun DragGripDots(sceneName: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier =
            modifier.size(width = 12.dp, height = 18.dp).semantics {
                contentDescription = "Drag to reorder $sceneName"
            }
    ) {
        val radius = 1.6.dp.toPx()
        val stepX = size.width - 2 * radius
        val stepY = (size.height - 2 * radius) / 2
        for (row in 0..2) {
            for (col in 0..1) {
                drawCircle(
                    color = Color.Gray,
                    radius = radius,
                    center = Offset(radius + col * stepX, radius + row * stepY),
                )
            }
        }
    }
}
