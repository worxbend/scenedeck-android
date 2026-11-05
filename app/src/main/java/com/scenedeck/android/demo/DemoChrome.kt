package com.scenedeck.android.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons

/** App-only exit/navigation callback; features only receive the read-only simulation marker. */
internal val LocalExitDemo = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun DemoFrame(
    phase: DemoPhase,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            DemoBanner(onExit)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    DemoPhase.READY -> content()
                    DemoPhase.FAILED ->
                        DemoUnavailable("Demo could not start. Try again or exit.", onExit, onRetry)
                    else ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text("Preparing local demo…")
                            }
                        }
                }
            }
        }
    }
}

@Composable
internal fun DemoBanner(onExit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp)
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    "DEMO · Local simulation",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text("No broadcast or recording", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Exit demo")
            }
        }
    }
}

@Composable
internal fun DemoUnavailable(
    message: String =
        "Connections, setup and background services are unavailable in demo. " +
            "Exit to use your real OBS profiles.",
    onExit: () -> Unit = LocalExitDemo.current ?: {},
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StudioPageHeader("Local demo", "Practice with sample scenes", SceneDeckIcons.Scenes)
        Text(message)
        Text(
            "Demo changes are temporary and separate from your real connections. " +
                "An existing real OBS session continues separately."
        )
        onRetry?.let { Button(onClick = it) { Text("Retry demo") } }
        Button(onClick = onExit) { Text("Exit demo") }
    }
}

@Composable
internal fun DemoEntryDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Try SceneDeck offline") },
        text = {
            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Practice Scenes, Mixer, Studio mode and output controls with a local simulation. " +
                        "Demo cannot broadcast or create recordings."
                )
                Text(
                    "Your real OBS session continues separately. " +
                        "Demo changes are temporary and never change your real profiles or curation."
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open demo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
