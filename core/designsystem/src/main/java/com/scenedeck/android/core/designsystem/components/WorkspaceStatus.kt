package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily

/** Shared inline state; a failed lookup must never look like a successfully empty collection. */
@Composable
fun WorkspaceStatus(failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = SceneDeckTheme.spacing
    Column(
        modifier.fillMaxWidth().padding(spacing.sectionGap).semantics {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap),
    ) {
        if (!failed) CircularProgressIndicator()
        Text(
            if (failed) "Couldn't load this workspace" else "Loading workspace…",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "Controls return when this OBS collection is ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) { Text("Retry workspace") }
    }
}

@PreviewLightDark
@Composable
private fun WorkspaceStatusPreview() {
    SceneDeckTheme {
        Surface {
            Column {
                WorkspaceStatus(false, {})
                WorkspaceStatus(true, {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun WorkspaceStatusObsPreview() {
    SceneDeckTheme(family = ThemeFamily.OBS) {
        Surface {
            Column {
                WorkspaceStatus(false, {})
                WorkspaceStatus(true, {})
            }
        }
    }
}
