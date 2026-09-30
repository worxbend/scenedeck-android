package com.scenedeck.android.feature.live

import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.MotionLevel

@androidx.compose.runtime.Composable
internal fun OutputDeckFixture(deck: DeckState, telemetry: Telemetry, motion: MotionLevel) {
    LiveDeckContent(
        deckState = deck,
        telemetry = telemetry,
        pendingScene = null,
        hapticsEnabled = false,
        motionLevel = motion,
        thumbnails = emptyMap(),
        onSceneTap = {},
        onStreamClick = {},
        onRecordClick = {},
        onToggleVirtualCam = {},
        onToggleReplayBuffer = {},
        onSaveReplay = {},
        onQuickEditSave = { _, _, _, _ -> },
        onReorder = {},
        onStudioToggle = {},
        onTransitionClick = {},
        onCutClick = {},
        onTransitionSelect = {},
        onTransitionDurationChange = {},
        previewsEnabled = true,
        onPreviewsToggle = {},
    )
}
