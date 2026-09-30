package com.scenedeck.android.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * App-level "Background" sheet, opened by long-pressing the StatusStrip connection indicator. Lives
 * in :app (not feature/settings) because the keep-alive flag is an app-private platform concern
 * (foreground service), persisted in [com.scenedeck.android.background.BackgroundSettingsStore].
 */
@Composable
fun BackgroundSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: BackgroundSettingsViewModel = hiltViewModel(),
) {
    val keepAlive by viewModel.keepAlive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.setKeepAlive(true)
        }

    BackgroundSettingsSheetContent(
        keepAlive = keepAlive,
        onKeepAliveChange = { enabled ->
            val needsPermission =
                enabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setKeepAlive(enabled)
            }
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundSettingsSheetContent(
    keepAlive: Boolean,
    onKeepAliveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Background",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                supportingContent = {
                    Text(
                        "Stays connected to OBS when SceneDeck is closed, with a " +
                            "persistent notification. Uses more battery."
                    )
                },
                trailingContent = {
                    Switch(checked = keepAlive, onCheckedChange = onKeepAliveChange)
                },
            ) {
                Text("Keep connection alive")
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun BackgroundSettingsSheetPreview() {
    SceneDeckTheme {
        BackgroundSettingsSheetContent(keepAlive = true, onKeepAliveChange = {}, onDismiss = {})
    }
}
