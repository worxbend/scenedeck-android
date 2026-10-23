package com.scenedeck.android.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.scenedeck.android.MainActivity
import com.scenedeck.android.R
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus

/**
 * Ongoing keep-alive notification: connection state, output state (LIVE/REC with elapsed time, per
 * the persistent status bar contract) and the current program scene.
 */
object SessionNotification {
    const val CHANNEL_ID = "obs_session"
    const val NOTIFICATION_ID = 42
    private const val TIMECODE_LENGTH = 8

    /**
     * Pure text mapping (unit-tested): title stays the app name; text carries state. Outputs are
     * only described while Ready and once both readings are known; unknown output state is never
     * reported as idle.
     */
    fun textFor(
        state: ConnectionState,
        programScene: String?,
        stream: StreamStatus? = null,
        record: RecordStatus? = null,
    ): String =
        when (state) {
            is ConnectionState.Ready -> readyText(programScene, stream, record)

            is ConnectionState.Connecting,
            is ConnectionState.Identifying -> "Connecting to OBS…"
            is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})…"
            is ConnectionState.Failed -> "Connection failed — tap to open SceneDeck"
            ConnectionState.Disconnected -> "Disconnected"
        }

    private fun readyText(
        programScene: String?,
        stream: StreamStatus?,
        record: RecordStatus?,
    ): String {
        val outputs = outputText(stream, record)
        return when {
            outputs != null -> listOfNotNull(outputs, programScene).joinToString(" · ")
            stream != null && record != null ->
                listOfNotNull("Connected", "OFF AIR", programScene).joinToString(" · ")
            else -> programScene?.let { "Connected · $it" } ?: "Connected to OBS"
        }
    }

    /** "LIVE 00:12:03", "REC 00:05:00", both, or null when no output is known to be active. */
    private fun outputText(stream: StreamStatus?, record: RecordStatus?): String? {
        val live =
            stream
                ?.takeIf { it.active }
                ?.let {
                    val label = if (it.reconnecting) "LIVE (reconnecting)" else "LIVE"
                    "$label ${it.timecode.take(TIMECODE_LENGTH)}"
                }
        val rec =
            record
                ?.takeIf { it.active }
                ?.let {
                    val label = if (it.paused) "REC (paused)" else "REC"
                    "$label ${it.timecode.take(TIMECODE_LENGTH)}"
                }
        return listOfNotNull(live, rec).joinToString(" · ").ifEmpty { null }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "OBS session", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun build(context: Context, text: String): Notification {
        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val disconnect =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(NotificationActionReceiver.ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("SceneDeck")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Disconnect", disconnect)
            .build()
    }
}
