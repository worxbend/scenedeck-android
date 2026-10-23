package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/** Bright synthetic frame: worst case for the number and icon well legibility. */
private fun brightPreviewFrame(): ImageBitmap {
    val image = ImageBitmap(320, 180)
    val canvas = Canvas(image)
    val paint = Paint()
    paint.color = Color(0xFFF5F1E6)
    canvas.drawRect(0f, 0f, 320f, 180f, paint)
    paint.color = Color(0xFFFFD54F)
    canvas.drawRect(0f, 0f, 320f, 60f, paint)
    return image
}

@PreviewLightDark
@Composable
private fun SceneCardThumbnailPreview() {
    SceneDeckTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SceneCard(
                label = "Camera",
                icon = SceneDeckIcons.Scenes,
                active = false,
                thumbnail = brightPreviewFrame(),
                sceneNumber = 2,
                modifier = Modifier.width(170.dp),
            )
            SceneCard(
                label = "Intro",
                icon = SceneDeckIcons.Scenes,
                active = false,
                reordering = true,
                modifier = Modifier.width(170.dp),
            )
        }
    }
}
