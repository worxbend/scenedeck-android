package com.scenedeck.android.feature.history

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.scenedeck.android.core.data.BookmarkOutputState
import com.scenedeck.android.core.data.BookmarkOutputTime
import com.scenedeck.android.core.data.BookmarkTimes
import com.scenedeck.android.core.data.HistoryCapturePhase
import com.scenedeck.android.core.data.HistoryCaptureState
import com.scenedeck.android.core.data.HistoryDetail
import com.scenedeck.android.core.data.HistoryDetailState
import com.scenedeck.android.core.data.HistoryEndReason
import com.scenedeck.android.core.data.HistoryEvent
import com.scenedeck.android.core.data.HistoryGapReason
import com.scenedeck.android.core.data.HistoryLinkedBookmark
import com.scenedeck.android.core.data.HistoryOutput
import com.scenedeck.android.core.data.HistoryOutputState
import com.scenedeck.android.core.data.HistoryPayload
import com.scenedeck.android.core.data.HistoryPreferences
import com.scenedeck.android.core.data.HistoryPresetRun
import com.scenedeck.android.core.data.HistorySession
import com.scenedeck.android.core.data.HistoryState
import com.scenedeck.android.core.data.LocalBookmark
import com.scenedeck.android.core.data.PresetOutput
import com.scenedeck.android.core.data.PresetRun
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetRunStep
import com.scenedeck.android.core.data.PresetSceneDestination
import com.scenedeck.android.core.data.PresetStep
import com.scenedeck.android.core.data.PresetStepResult
import com.scenedeck.android.core.data.PresetStopReason
import com.scenedeck.android.core.data.WorkspaceKey
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily

internal fun historyPreviewState(detail: Boolean = false): HistoryUiState {
    val at = 1_793_843_000_000L
    val workspace = WorkspaceKey(7, "Evening show")
    val session =
        HistorySession(
            "preview",
            workspace,
            at,
            at + 65_000,
            65_000,
            at + 65_000,
            HistoryEndReason.DISCONNECTED,
            5,
            0,
            2,
        )
    val observation =
        HistoryDetail(
            session,
            listOf(
                HistoryEvent(1, at, 0, 0, HistoryPayload.Program("Opening camera", true)),
                HistoryEvent(
                    2,
                    at + 5_000,
                    5_000,
                    0,
                    HistoryPayload.Output(
                        HistoryOutput.STREAM,
                        HistoryOutputState.ACTIVE,
                        false,
                        5_000,
                    ),
                ),
                HistoryEvent(
                    5,
                    at + 60_000,
                    60_000,
                    2,
                    HistoryPayload.Gap(HistoryGapReason.CONNECTION_UNAVAILABLE),
                ),
            ),
            listOf(historyPreviewBookmark(workspace, at)),
            listOf(historyPreviewPreset(at)),
        )
    return HistoryUiState(
        HistoryState.Ready(HistoryPreferences(), listOf(session)),
        HistoryCaptureState(HistoryCapturePhase.WAITING),
        mapOf(7L to "Studio rig"),
        if (detail) HistorySelection(session.id, HistoryDetailState.Ready(observation))
        else HistorySelection(),
    )
}

internal fun historyPreviewPreset(at: Long) =
    HistoryPresetRun(
        55_000,
        PresetRun(
            "run",
            "preset",
            "Interview start",
            2,
            at + 55_000,
            listOf(
                PresetRunStep(PresetStep.Mute("Guest mic", false), PresetStepResult.CONFIRMED),
                PresetRunStep(
                    PresetStep.Output(PresetOutput.RECORDING, true),
                    PresetStepResult.UNKNOWN,
                ),
                PresetRunStep(
                    PresetStep.Scene("Guest camera", PresetSceneDestination.PROGRAM),
                    PresetStepResult.NOT_RUN,
                ),
            ),
            7,
            PresetRunOutcome.STOPPED,
            PresetStopReason.WORKSPACE_CHANGED,
            at + 66_000,
        ),
    )

private fun historyPreviewBookmark(workspace: WorkspaceKey, at: Long) =
    HistoryLinkedBookmark(
        LocalBookmark(
            "mark",
            workspace,
            "bookmark-observation",
            at + 30_000,
            "Guest introduction",
            BookmarkTimes(
                BookmarkOutputTime(BookmarkOutputState.ACTIVE, 90_000, 100),
                BookmarkOutputTime(BookmarkOutputState.PAUSED, 50_000, 100),
            ),
        ),
        30_000,
    )

@Composable
private fun HistoryPreview(detail: Boolean, family: ThemeFamily) {
    SceneDeckTheme(family = family) {
        Surface {
            HistoryContent(historyPreviewState(detail), {}, { _, _ -> }, {}, {}, {}, {})
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryListPreview() = HistoryPreview(false, ThemeFamily.SCENEDECK)

@PreviewLightDark
@Composable
private fun HistoryDetailPreview() = HistoryPreview(true, ThemeFamily.SCENEDECK)

@PreviewLightDark
@Composable
private fun ObsHistoryListPreview() = HistoryPreview(false, ThemeFamily.OBS)

@PreviewLightDark
@Composable
private fun ObsHistoryDetailPreview() = HistoryPreview(true, ThemeFamily.OBS)
