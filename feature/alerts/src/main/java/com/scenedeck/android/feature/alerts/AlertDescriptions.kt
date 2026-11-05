package com.scenedeck.android.feature.alerts

import com.scenedeck.android.core.data.AlertCondition
import com.scenedeck.android.core.data.AlertReading
import com.scenedeck.android.core.data.LiveAlert
import com.scenedeck.android.core.data.LiveAlertKind
import java.util.Locale

internal val LiveAlertKind.label: String
    get() =
        when (this) {
            LiveAlertKind.CONNECTION -> "OBS connection"
            LiveAlertKind.DROPPED_FRAMES -> "Stream frame drops"
            LiveAlertKind.RENDER_LOAD -> "Render time"
            LiveAlertKind.DISK_SPACE -> "Recording space"
            LiveAlertKind.MICROPHONE -> "Microphone signal"
        }

internal val LiveAlert.title: String
    get() =
        when (kind) {
            LiveAlertKind.CONNECTION -> "Connection lost — output state unknown"
            LiveAlertKind.DROPPED_FRAMES -> "Stream frames are dropping"
            LiveAlertKind.RENDER_LOAD -> "Rendering is taking too long"
            LiveAlertKind.DISK_SPACE -> "Recording space is low"
            LiveAlertKind.MICROPHONE -> "No microphone signal detected"
        }.let { if (uncertain) "$it · readings unavailable" else it }

internal val LiveAlertKind.destination: AlertDestination
    get() =
        when (this) {
            LiveAlertKind.CONNECTION -> AlertDestination.CONNECTIONS
            LiveAlertKind.MICROPHONE -> AlertDestination.MIXER
            LiveAlertKind.DISK_SPACE -> AlertDestination.PREFLIGHT
            else -> AlertDestination.STATS
        }

internal val AlertDestination.actionLabel: String
    get() =
        when (this) {
            AlertDestination.STATS -> "Open Stats"
            AlertDestination.MIXER -> "Open Mixer"
            AlertDestination.CONNECTIONS -> "Connections"
            AlertDestination.PREFLIGHT -> "Open Preflight"
        }

internal val AlertReading.description: String
    get() =
        when (condition) {
            AlertCondition.INACTIVE ->
                when (kind) {
                    LiveAlertKind.CONNECTION -> "Connected"
                    LiveAlertKind.DROPPED_FRAMES -> "Not streaming"
                    LiveAlertKind.RENDER_LOAD -> "Waiting for streaming or recording"
                    LiveAlertKind.DISK_SPACE -> "Not recording"
                    LiveAlertKind.MICROPHONE -> "Not armed"
                }
            AlertCondition.UNKNOWN -> "Unknown · waiting for fresh readings"
            else -> readingDetail()
        }

private fun AlertReading.readingDetail(): String {
    val number = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
    val limit = threshold?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
    return when (kind) {
        LiveAlertKind.CONNECTION -> "Output state is unknown. Check OBS and the network."
        LiveAlertKind.DROPPED_FRAMES -> "$number% of recent frames · warns at 1%"
        LiveAlertKind.RENDER_LOAD -> "$number ms per frame · threshold $limit ms"
        LiveAlertKind.DISK_SPACE -> "$number MB free · minimum $limit MB"
        LiveAlertKind.MICROPHONE ->
            if (condition == AlertCondition.CLEAR) "Signal detected"
            else "$number seconds of quiet · warns at 10 seconds"
    }
}
