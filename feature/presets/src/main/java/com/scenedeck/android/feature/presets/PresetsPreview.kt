package com.scenedeck.android.feature.presets

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.data.ActionPreset
import com.scenedeck.android.core.data.OutputSafety
import com.scenedeck.android.core.data.PresetCheck
import com.scenedeck.android.core.data.PresetHistoryStatus
import com.scenedeck.android.core.data.PresetOutput
import com.scenedeck.android.core.data.PresetPlan
import com.scenedeck.android.core.data.PresetPolicy
import com.scenedeck.android.core.data.PresetRun
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetRunState
import com.scenedeck.android.core.data.PresetRunStep
import com.scenedeck.android.core.data.PresetSceneDestination
import com.scenedeck.android.core.data.PresetState
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.PresetStepResult
import com.scenedeck.android.core.data.PresetStopReason
import com.scenedeck.android.core.data.PresetTargets
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ObsInputSummary
import com.scenedeck.android.core.model.ObsSessionToken

internal fun presetsPreviewState(page: PresetPreviewPage): PresetsUiState {
    val origin = WorkspaceSession(WorkspaceKey(7, "Studio"), 1, ObsSessionToken())
    val preset =
        ActionPreset(
            origin.key,
            "interview",
            "Interview wrap",
            0,
            listOf(
                PresetStep.Scene("Camera 01", PresetSceneDestination.PROGRAM),
                PresetStep.Mute("Mic/Aux", true),
                PresetStep.Output(PresetOutput.RECORDING, false),
            ),
        )
    val workspace = previewWorkspace(origin, preset)
    val editor =
        PresetEditorState(
            id = "editor",
            origin = origin,
            original = preset,
            draft = preset.draft(),
            steps =
                preset.steps.mapIndexed { index, step -> PresetEditorStep("step-$index", step) },
        )
    val plan =
        PresetPlan(
            origin,
            preset,
            0,
            listOf(PresetCheck.READY, PresetCheck.READY, PresetCheck.READY),
            PresetPolicy(emptySet(), emptySet(), OutputSafety()),
        )
    return PresetsUiState(
        workspace = workspace,
        edits = previewEditor(page, editor),
        review =
            if (page == PresetPreviewPage.REVIEW)
                PresetReviewUi(
                    PresetReview(
                        id = "review",
                        origin = origin,
                        preset = preset,
                        phase = PresetReviewPhase.READY,
                        plan = plan,
                    )
                )
            else PresetReviewUi(),
        run = if (page == PresetPreviewPage.RESULT) previewRun(origin, preset) else null,
    )
}

private fun previewEditor(page: PresetPreviewPage, editor: PresetEditorState) =
    when (page) {
        PresetPreviewPage.EDITOR -> PresetEditUi(editor = editor)
        PresetPreviewPage.STEP ->
            PresetEditUi(
                editor =
                    editor.copy(
                        stepDraft =
                            PresetStepEditor(
                                id = "step",
                                kind = PresetStepKind.VOLUME,
                                target = "Mic/Aux",
                                volumePercent = "75",
                            )
                    )
            )
        else -> PresetEditUi()
    }

private fun previewWorkspace(origin: WorkspaceSession, preset: ActionPreset): PresetWorkspaceUi {
    return PresetWorkspaceUi(
        saved = PresetState.Ready(origin, listOf(preset)),
        targets =
            PresetTargets.Ready(
                origin,
                listOf(
                    ObsInputSummary("Mic/Aux", "pulse_input_capture"),
                    ObsInputSummary("Off-program clip", "ffmpeg_source"),
                ),
            ),
        scenes =
            listOf(
                SceneCardState(
                    "Camera 01",
                    SceneRole.PRIMARY,
                    null,
                    null,
                    0,
                    true,
                    localAlias = "Host close-up",
                )
            ),
        profileNames = mapOf(7L to "Main studio"),
    )
}

private fun previewRun(origin: WorkspaceSession, preset: ActionPreset) =
    PresetRunState(
        origin,
        PresetRun(
            "run",
            preset.id,
            preset.name,
            0,
            1_000,
            listOf(
                PresetRunStep(preset.steps[0], PresetStepResult.CONFIRMED),
                PresetRunStep(preset.steps[1], PresetStepResult.UNKNOWN),
                PresetRunStep(preset.steps[2], PresetStepResult.NOT_RUN),
            ),
            5,
            PresetRunOutcome.STOPPED,
            PresetStopReason.WRITE_UNKNOWN,
            1_100,
        ),
        PresetHistoryStatus.SAVED,
    )

@PreviewLightDark
@Composable
private fun PresetsEditorPreview() {
    SceneDeckTheme {
        Surface { PresetsContent(presetsPreviewState(PresetPreviewPage.EDITOR), {}, {}) }
    }
}

@PreviewLightDark
@Composable
private fun PresetsReviewPreview() {
    SceneDeckTheme {
        Surface { PresetsContent(presetsPreviewState(PresetPreviewPage.REVIEW), {}, {}) }
    }
}
