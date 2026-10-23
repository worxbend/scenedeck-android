package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Screen snackbar host: place it bottom-aligned in the screen's root box. It pads by the horizontal
 * and bottom safe-drawing insets that the app shell has not already consumed (the shell consumes
 * the bars below the status strip), so messages stay above the strip, navigation and keyboard and
 * never draw into the status bar or a cutout.
 */
@Composable
fun StudioSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
    )
}

@Preview(widthDp = 360, heightDp = 200)
@Composable
private fun StudioSnackbarHostPreview() {
    SceneDeckTheme {
        val hostState = remember { SnackbarHostState() }
        LaunchedEffect(hostState) { hostState.showSnackbar("Removed stale entry") }
        Box(Modifier.fillMaxSize()) {
            StudioSnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Static render of the snackbar the host shows (the host preview animates in interactive mode). */
@Preview(widthDp = 360)
@Composable
private fun StudioSnackbarPreview() {
    SceneDeckTheme { Snackbar(Modifier.fillMaxWidth()) { Text("Removed stale entry") } }
}
