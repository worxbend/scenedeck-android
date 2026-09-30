package com.scenedeck.android.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.preview.DesignSystemPreview
import com.scenedeck.android.core.designsystem.theme.SceneDeckShapeTokens
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.families.displayName
import com.scenedeck.android.core.designsystem.theme.mono

/**
 * Design-system sampler for a single [ThemeFamily]: scheme colors, semantic (product) colors,
 * typography, shapes and the icon catalogue. Used by previews and Roborazzi goldens.
 */
@Composable
fun DesignSystemGallery(
    family: ThemeFamily,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    SceneDeckTheme(family = family, darkTheme = darkTheme) {
        Surface(modifier = modifier) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(family.displayName, style = MaterialTheme.typography.headlineSmall)

                GallerySection("Scheme") {
                    ColorChipRow(
                        "primary" to MaterialTheme.colorScheme.primary,
                        "secondary" to MaterialTheme.colorScheme.secondary,
                        "tertiary" to MaterialTheme.colorScheme.tertiary,
                        "surface" to MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                GallerySection("Semantics") {
                    val semantic = SceneDeckTheme.colors
                    ColorChipRow(
                        "program" to semantic.program,
                        "preview" to semantic.preview,
                        "rec" to semantic.recording,
                        "warn" to semantic.warning,
                    )
                    ColorChipRow(
                        "meter" to semantic.meterGreen,
                        "" to semantic.meterYellow,
                        "" to semantic.meterRed,
                        "idle" to semantic.idle,
                    )
                }

                GallerySection("Type") {
                    Text("Scene name", style = MaterialTheme.typography.titleLarge)
                    Text("Body copy in Inter.", style = MaterialTheme.typography.bodyMedium)
                    Text("-12.4 dB  00:01:23", style = MaterialTheme.typography.mono)
                }

                GallerySection("Shapes") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShapeSample(SceneDeckShapeTokens.SceneCard)
                        ShapeSample(SceneDeckShapeTokens.MixerStrip)
                        ShapeSample(SceneDeckShapeTokens.Transport)
                    }
                }

                GallerySection("Icons") {
                    IconRow(
                        SceneDeckIcons.Scenes,
                        SceneDeckIcons.Mixer,
                        SceneDeckIcons.Stats,
                        SceneDeckIcons.Inventory,
                        SceneDeckIcons.Graph,
                        SceneDeckIcons.Doctor,
                        SceneDeckIcons.Settings,
                        SceneDeckIcons.Connections,
                        SceneDeckIcons.Help,
                    )
                    IconRow(
                        SceneIcon.CAMERA.imageVector,
                        SceneIcon.MIC.imageVector,
                        SceneIcon.GAMEPAD.imageVector,
                        SceneIcon.MUSIC.imageVector,
                        SceneIcon.STREAM.imageVector,
                        SceneIcon.RECORD.imageVector,
                        SceneIcon.STAR.imageVector,
                        SceneIcon.COFFEE.imageVector,
                        SceneIcon.SPARKLES.imageVector,
                    )
                }
            }
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ColorChipRow(vararg chips: Pair<String, Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (label, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(color))
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeSample(shape: Shape) {
    Box(
        Modifier.size(width = 56.dp, height = 40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    )
}

@Composable
private fun IconRow(vararg icons: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        icons.forEach { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@DesignSystemPreview
@Composable
private fun DesignSystemGalleryPreview() {
    DesignSystemGallery(family = ThemeFamily.SCENEDECK)
}

@DesignSystemPreview
@Composable
private fun DesignSystemGalleryObsPreview() {
    DesignSystemGallery(family = ThemeFamily.OBS)
}
