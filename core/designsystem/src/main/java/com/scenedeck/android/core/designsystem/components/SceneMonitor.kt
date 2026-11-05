package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/** Read-only scene context. A cached snapshot never implies live video or a new command. */
@Composable
fun SceneMonitor(
    label: String,
    name: String?,
    rawName: String?,
    preview: Boolean,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    imageStatus: String? = null,
) {
    val tally = if (preview) SceneDeckTheme.colors.preview else SceneDeckTheme.colors.program
    Surface(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, tally.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(SceneDeckTheme.spacing.rowPadding)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = tally,
                fontWeight = FontWeight.Bold,
            )
            if (image != null)
                Image(
                    image,
                    null,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(vertical = 8.dp),
                    contentScale = ContentScale.Fit,
                )
            Text(
                name ?: "Unknown scene",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (rawName != null && name != rawName)
                Text("OBS · $rawName", style = MaterialTheme.typography.bodySmall)
            imageStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
