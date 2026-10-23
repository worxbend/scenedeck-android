package com.scenedeck.android.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * App-level "Background" sheet, opened from More (Keep-alive & background) or by long-pressing the
 * StatusStrip connection indicator. Lives in :app (not feature/settings) because the keep-alive
 * flag is an app-private platform concern (foreground service), persisted in
 * [com.scenedeck.android.background.BackgroundSettingsStore].
 *
 * Notification permission (API 33+) is requested only when enabling keep-alive. A denial is
 * explained in place; a permanent denial ("Don't ask again") offers the app notification settings,
 * and returning with notifications allowed finishes enabling keep-alive.
 */
@Composable
fun BackgroundSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: BackgroundSettingsViewModel = hiltViewModel(),
) {
    val keepAlive by viewModel.keepAlive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsEnabled by
        remember(context) { mutableStateOf(areNotificationsEnabled(context)) }
    var denial by rememberSaveable { mutableStateOf<NotificationPermissionDenial?>(null) }
    var rationaleBeforeRequest by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsEnabled = areNotificationsEnabled(context)
            if (granted) {
                denial = null
                viewModel.setKeepAlive(true)
            } else {
                denial =
                    classifyDenial(
                        before = rationaleBeforeRequest,
                        after = shouldShowNotificationRationale(context),
                        deniedEarlier = denial != null,
                    )
            }
        }

    // Back from system settings: refresh, and finish the enable the user was blocked on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = areNotificationsEnabled(context)
        if (denial != null && !needsNotificationPermission(context)) {
            denial = null
            viewModel.setKeepAlive(true)
        }
    }

    BackgroundSettingsSheetContent(
        keepAlive = keepAlive,
        notice = keepAliveNotice(keepAlive, notificationsEnabled, denial),
        onKeepAliveChange = { enabled ->
            if (enabled && needsNotificationPermission(context)) {
                rationaleBeforeRequest = shouldShowNotificationRationale(context)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                if (!enabled) denial = null
                viewModel.setKeepAlive(enabled)
            }
        },
        onOpenNotificationSettings = { openNotificationSettings(context) },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundSettingsSheetContent(
    keepAlive: Boolean,
    notice: KeepAliveNotice,
    onKeepAliveChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudioSectionHeader("Keep-alive & background")
            KeepAliveToggleRow(checked = keepAlive, onCheckedChange = onKeepAliveChange)
            if (notice != KeepAliveNotice.NONE) {
                KeepAliveNoticePanel(notice, onOpenNotificationSettings)
            }
        }
    }
}

/** Whole row toggles (AGENTS.md: settings toggle rows are tappable). */
@Composable
private fun KeepAliveToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudioIconWell(icon = SceneDeckIcons.Connections, tone = StudioTone.SECONDARY)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Keep connection alive", style = MaterialTheme.typography.titleSmall)
            Text(
                "Stays connected to OBS when SceneDeck is closed, with a persistent " +
                    "notification. Uses more battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun KeepAliveNoticePanel(notice: KeepAliveNotice, onOpenNotificationSettings: () -> Unit) {
    val blocking = notice != KeepAliveNotice.NOTIFICATIONS_OFF
    Surface(
        shape = MaterialTheme.shapes.large,
        color =
            if (blocking) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor =
            if (blocking) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(notice.message(), style = MaterialTheme.typography.bodyMedium)
            if (notice != KeepAliveNotice.PERMISSION_DENIED) {
                FilledTonalButton(onClick = onOpenNotificationSettings) {
                    Text("Open notification settings")
                }
            }
        }
    }
}

private fun KeepAliveNotice.message(): String =
    when (this) {
        KeepAliveNotice.PERMISSION_DENIED ->
            "Keep-alive needs permission to show its session notification. Turn the switch " +
                "on again to allow notifications."
        KeepAliveNotice.PERMISSION_BLOCKED ->
            "Notifications are blocked for SceneDeck, so keep-alive can't start. Allow " +
                "notifications in system settings; keep-alive turns on when you return."
        KeepAliveNotice.NOTIFICATIONS_OFF ->
            "Notifications are off for SceneDeck, so the keep-alive notification and its " +
                "Disconnect action are hidden."
        KeepAliveNotice.NONE -> ""
    }

@PreviewLightDark
@Composable
private fun BackgroundSettingsSheetPreview() {
    SceneDeckTheme {
        BackgroundSettingsSheetContent(
            keepAlive = true,
            notice = KeepAliveNotice.NONE,
            onKeepAliveChange = {},
            onOpenNotificationSettings = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun BackgroundSettingsSheetBlockedPreview() {
    SceneDeckTheme {
        BackgroundSettingsSheetContent(
            keepAlive = false,
            notice = KeepAliveNotice.PERMISSION_BLOCKED,
            onKeepAliveChange = {},
            onOpenNotificationSettings = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun BackgroundSettingsSheetDeniedPreview() {
    SceneDeckTheme {
        BackgroundSettingsSheetContent(
            keepAlive = false,
            notice = KeepAliveNotice.PERMISSION_DENIED,
            onKeepAliveChange = {},
            onOpenNotificationSettings = {},
            onDismiss = {},
        )
    }
}
