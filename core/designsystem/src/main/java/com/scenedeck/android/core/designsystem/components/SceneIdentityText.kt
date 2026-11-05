package com.scenedeck.android.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun SceneIdentityText(label: String, obsName: String?, foreground: Color) {
    val lines = if (obsName == null) 2 else 1
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = foreground,
        minLines = lines,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
    if (obsName != null)
        Text(
            "OBS · $obsName",
            style = MaterialTheme.typography.labelSmall,
            color = foreground.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
}
