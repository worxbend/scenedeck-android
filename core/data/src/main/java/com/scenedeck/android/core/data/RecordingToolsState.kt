package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.RecordStatus

data class RecordingOrigin(val targetGeneration: Long, val session: ObsSessionToken, val run: Long)

enum class RecordingAction {
    PAUSE,
    RESUME,
    SPLIT,
    CHAPTER,
}

enum class RecordingResult {
    CONFIRMED,
    ACCEPTED,
    REJECTED,
    UNKNOWN,
    CHANGED,
}

data class RecordingNotice(val action: RecordingAction, val result: RecordingResult)

data class RecordingToolsState(
    val origin: RecordingOrigin? = null,
    val record: RecordStatus? = null,
    val capabilities: ObsCapabilities = ObsCapabilities(),
    val pending: RecordingAction? = null,
    val busy: Boolean = false,
    val notice: RecordingNotice? = null,
) {
    fun canRun(action: RecordingAction): Boolean =
        origin != null &&
            !busy &&
            pending == null &&
            record?.active == true &&
            when (action) {
                RecordingAction.PAUSE -> capabilities.pauseRecording && !record.paused
                RecordingAction.RESUME -> capabilities.resumeRecording && record.paused
                RecordingAction.SPLIT -> capabilities.splitRecording && !record.paused
                RecordingAction.CHAPTER -> capabilities.recordingChapter && !record.paused
            }
}
