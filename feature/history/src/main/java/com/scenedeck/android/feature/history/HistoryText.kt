package com.scenedeck.android.feature.history

import com.scenedeck.android.core.data.HistoryCapturePhase
import com.scenedeck.android.core.data.HistoryCaptureState
import com.scenedeck.android.core.data.HistoryDetail
import com.scenedeck.android.core.data.HistoryEndReason
import com.scenedeck.android.core.data.HistoryGapReason
import com.scenedeck.android.core.data.HistoryOutput
import com.scenedeck.android.core.data.HistoryOutputState
import com.scenedeck.android.core.data.HistoryPayload
import com.scenedeck.android.core.data.HistorySession
import com.scenedeck.android.core.data.HistoryWarning
import com.scenedeck.android.core.data.HistoryWarningPhase
import java.util.Locale

internal fun historyElapsed(ms: Long): String =
    String.format(
        Locale.ROOT,
        "%02d:%02d:%02d",
        ms.coerceAtLeast(0) / 3_600_000,
        ms.coerceAtLeast(0) / 60_000 % 60,
        ms.coerceAtLeast(0) / 1_000 % 60,
    )

internal fun HistorySession.endText(capture: HistoryCaptureState): String =
    when (endReason) {
        HistoryEndReason.DISCONNECTED -> "Disconnected from OBS"
        HistoryEndReason.WORKSPACE_CHANGED -> "Connection or collection changed"
        HistoryEndReason.CAPTURE_DISABLED -> "Capture disabled · end time unknown"
        HistoryEndReason.PROCESS_ENDED -> "App observation interrupted · end time unknown"
        HistoryEndReason.STORAGE_FAILURE -> "Capture interrupted · end time unknown"
        HistoryEndReason.CAPTURE_INTERRUPTED -> "History capture interrupted · end time unknown"
        HistoryEndReason.CAPTURE_OVERFLOW -> "History couldn't keep up · end time unknown"
        null ->
            if (capture.phase == HistoryCapturePhase.CAPTURING && capture.sessionId == id)
                "Observing now"
            else "Observation end not yet known"
    }

internal fun HistoryDetail.timeline(): List<HistoryTimelineItem> {
    val changes = events.map { event ->
        val missing =
            if (event.missingBefore > 0)
                listOf("${event.missingBefore} history entries missed before this point.")
            else emptyList()
        HistoryTimelineItem(
            "event-${event.ordinal}",
            event.elapsedMs,
            event.observedAtMs,
            event.payload.title(),
            missing + event.payload.description(),
            event.ordinal,
        )
    }
    val moments = bookmarks.map { linked ->
        val bookmark = linked.bookmark
        HistoryTimelineItem(
            "bookmark-${bookmark.id}",
            linked.elapsedMs,
            bookmark.createdAtMs,
            "Bookmark · ${bookmark.label}",
            listOf(
                outputTimeText("Stream", bookmark.times.stream),
                outputTimeText("Recording", bookmark.times.recording),
            ),
            Long.MAX_VALUE,
        )
    }
    return (changes + moments + presetRuns.map { it.timelineItem() }).sortedWith(
        compareBy({ it.elapsedMs }, { it.order })
    )
}

private fun HistoryPayload.title(): String =
    when (this) {
        is HistoryPayload.Program -> "Program · ${sceneName ?: "unknown"}"
        is HistoryPayload.Output -> "${output.label()} · ${state.label()}"
        is HistoryPayload.Warning -> "Warning · ${warning.label()}"
        is HistoryPayload.Gap -> "Observation gap"
    }

private fun HistoryPayload.description(): List<String> =
    when (this) {
        is HistoryPayload.Program ->
            listOf(if (initial) "Initial program reading." else "Observed program change.")
        is HistoryPayload.Output ->
            buildList {
                add(if (initial) "Initial output reading." else "Observed output state.")
                durationMs?.let { add("Reported OBS time · ${historyElapsed(it)}") }
            }
        is HistoryPayload.Warning ->
            listOf(
                when (phase) {
                    HistoryWarningPhase.SHOWN -> "Warning observed."
                    HistoryWarningPhase.UNCERTAIN ->
                        "Evidence became unavailable; recovery is unknown."
                    HistoryWarningPhase.CLEARED_OR_DISMISSED ->
                        "Cleared, dismissed or monitoring stopped; recovery is not confirmed."
                }
            )
        is HistoryPayload.Gap ->
            listOf(
                when (reason) {
                    HistoryGapReason.CONNECTION_UNAVAILABLE ->
                        "Connection unavailable. Output state during this gap is unknown."
                    HistoryGapReason.WORKSPACE_UNAVAILABLE ->
                        "Workspace unavailable. Changes during this gap are unknown."
                    HistoryGapReason.OBSERVATION_INTERRUPTED ->
                        "Some changes were not observed or saved."
                }
            )
    }

private fun HistoryOutput.label(): String =
    when (this) {
        HistoryOutput.STREAM -> "Stream"
        HistoryOutput.RECORDING -> "Recording"
        HistoryOutput.VIRTUAL_CAMERA -> "Virtual camera"
    }

private fun HistoryOutputState.label(): String =
    when (this) {
        HistoryOutputState.UNKNOWN -> "unknown"
        HistoryOutputState.INACTIVE -> "inactive"
        HistoryOutputState.ACTIVE -> "active"
        HistoryOutputState.PAUSED -> "paused"
        HistoryOutputState.RECONNECTING -> "reconnecting"
    }

private fun HistoryWarning.label(): String =
    when (this) {
        HistoryWarning.CONNECTION -> "Connection"
        HistoryWarning.DROPPED_FRAMES -> "Dropped frames"
        HistoryWarning.RENDER_LOAD -> "Render load"
        HistoryWarning.DISK_SPACE -> "Disk space"
        HistoryWarning.MICROPHONE -> "Microphone"
    }
