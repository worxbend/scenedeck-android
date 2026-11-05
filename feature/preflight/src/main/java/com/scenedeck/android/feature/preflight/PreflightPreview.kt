package com.scenedeck.android.feature.preflight

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.data.ManualPreflightCheck
import com.scenedeck.android.core.data.PreflightConfig
import com.scenedeck.android.core.data.PreflightFinding
import com.scenedeck.android.core.data.PreflightReport
import com.scenedeck.android.core.data.PreflightSetupState
import com.scenedeck.android.core.data.PreflightStatus
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ObsSessionToken
import java.time.Instant

internal fun preflightPreviewState(): PreflightUiState {
    val origin = WorkspaceSession(WorkspaceKey(1, "Studio"), 1, ObsSessionToken())
    val config =
        PreflightConfig(
            "Studio",
            listOf("Camera"),
            "Mic/Aux",
            manualChecks = listOf(ManualPreflightCheck("lights", "Lights and framing ready")),
        )
    val target = PreflightSetupState.Ready(origin, config)
    val findings =
        listOf(
            PreflightFinding(
                "collection",
                "Scene collection",
                PreflightStatus.PASS,
                "Connected: Studio · expected: Studio.",
            ),
            PreflightFinding("scene:Camera", "Camera", PreflightStatus.PASS, "Present in OBS."),
            PreflightFinding(
                "microphone",
                "Microphone control",
                PreflightStatus.FAIL,
                "Mic/Aux is muted.",
            ),
            PreflightFinding(
                "disk",
                "Recording disk space",
                PreflightStatus.UNKNOWN,
                "Free space is unavailable.",
            ),
        )
    return PreflightUiState(
        target,
        PreflightRunState(
            target,
            report =
                PreflightReport(
                    origin,
                    Instant.parse("2026-11-05T10:00:00Z").toEpochMilli(),
                    findings,
                ),
        ),
        scenes =
            listOf(
                SceneCardState(
                    "Camera",
                    SceneRole.PRIMARY,
                    null,
                    null,
                    0,
                    false,
                    localAlias = "Host",
                )
            ),
        microphones = listOf("Mic/Aux", "Guest"),
        connectionName = "Home studio",
    )
}

@PreviewLightDark
@Composable
private fun PreflightPreview() {
    val state = preflightPreviewState()
    SceneDeckTheme {
        Surface {
            PreflightContent(
                state,
                state.setup as PreflightSetupState.Ready,
                {},
                {},
                {},
                { _, _ -> },
            )
        }
    }
}
