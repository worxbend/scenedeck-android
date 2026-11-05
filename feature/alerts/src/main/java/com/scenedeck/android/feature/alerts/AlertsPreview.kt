package com.scenedeck.android.feature.alerts

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.data.AlertCondition
import com.scenedeck.android.core.data.AlertReading
import com.scenedeck.android.core.data.LiveAlert
import com.scenedeck.android.core.data.LiveAlertKind
import com.scenedeck.android.core.data.LiveAlertsState
import com.scenedeck.android.core.data.SpeechTarget
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ObsSessionToken

internal fun alertsPreviewState() =
    LiveAlertsState(
        connected = true,
        alerts =
            listOf(
                LiveAlert(LiveAlertKind.RENDER_LOAD, 1),
                LiveAlert(LiveAlertKind.DISK_SPACE, 2, uncertain = true),
            ),
        readings =
            listOf(
                AlertReading(
                    LiveAlertKind.DROPPED_FRAMES,
                    AlertCondition.CLEAR,
                    value = 0.2,
                    threshold = 1.0,
                ),
                AlertReading(
                    LiveAlertKind.RENDER_LOAD,
                    AlertCondition.BAD,
                    value = 20.0,
                    threshold = 15.0,
                ),
                AlertReading(LiveAlertKind.DISK_SPACE, AlertCondition.UNKNOWN),
                AlertReading(LiveAlertKind.CONNECTION, AlertCondition.INACTIVE),
                AlertReading(LiveAlertKind.MICROPHONE, AlertCondition.INACTIVE),
            ),
        speechTarget =
            SpeechTarget(
                WorkspaceSession(WorkspaceKey(7, "Studio"), 1, ObsSessionToken()),
                "Mic/Aux",
            ),
        diskThresholdMb = 2_048,
    )

@PreviewLightDark
@Composable
private fun AlertsPreview() {
    SceneDeckTheme { Surface { AlertsContent(alertsPreviewState(), {}, {}, { true }, {}, {}) } }
}
