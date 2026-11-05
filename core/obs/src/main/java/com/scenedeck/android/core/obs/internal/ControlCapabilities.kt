package com.scenedeck.android.core.obs.internal

import com.scenedeck.android.core.model.ObsControl

/** Keep request names on the protocol side; missing reads cannot become verifiable controls. */
internal fun Collection<String>.controlCapabilities(): Set<ObsControl> {
    val advertised = toSet()
    val required =
        mapOf(
            ObsControl.PROGRAM_SCENE to
                setOf("GetSceneList", "GetCurrentProgramScene", "SetCurrentProgramScene"),
            ObsControl.PREVIEW_SCENE to
                setOf(
                    "GetSceneList",
                    "GetStudioModeEnabled",
                    "GetCurrentPreviewScene",
                    "SetCurrentPreviewScene",
                ),
            ObsControl.INPUT_MUTE to setOf("GetInputMute", "SetInputMute"),
            ObsControl.FOLLOW_STUDIO_SCENE to
                setOf(
                    "GetSceneList",
                    "GetStudioModeEnabled",
                    "GetCurrentProgramScene",
                    "GetCurrentPreviewScene",
                    "SetCurrentProgramScene",
                    "SetCurrentPreviewScene",
                ),
            ObsControl.STUDIO_TRANSITION to
                setOf(
                    "GetSceneList",
                    "GetStudioModeEnabled",
                    "GetCurrentProgramScene",
                    "GetCurrentPreviewScene",
                    "TriggerStudioModeTransition",
                ),
            ObsControl.INPUT_VOLUME to setOf("GetInputVolume", "SetInputVolume"),
            ObsControl.MEDIA_STATE to setOf("GetMediaInputStatus", "TriggerMediaInputAction"),
            ObsControl.STREAM to setOf("GetStreamStatus", "StartStream", "StopStream"),
            ObsControl.RECORDING to setOf("GetRecordStatus", "StartRecord", "StopRecord"),
        )
    return required.filterValues(advertised::containsAll).keys.toSet()
}
