package com.scenedeck.android.feature.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scenedeck.android.core.data.DeckState
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.Telemetry
import com.scenedeck.android.core.designsystem.theme.MotionLevel

@androidx.compose.runtime.Composable
internal fun OutputDeckFixture(deck: DeckState, telemetry: Telemetry, motion: MotionLevel) {
    var options by remember { mutableStateOf(NamedDeckSnapshot()) }
    LiveDeckContent(
        deckOptions = options,
        onDeckSelection = { options = options.copy(selection = it) },
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
        onQuickEditSave = { _, _ -> true },
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
