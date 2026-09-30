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

/** Ongoing keep-alive notification: connection state + current program scene. */
object SessionNotification {
    const val CHANNEL_ID = "obs_session"
    const val NOTIFICATION_ID = 42

    /** Pure text mapping (unit-tested): title stays the app name; text carries state. */
    fun textFor(state: ConnectionState, programScene: String?): String =
        when (state) {
            is ConnectionState.Ready ->
                programScene?.let { "Connected · $it" } ?: "Connected to OBS"

            is ConnectionState.Connecting,
            is ConnectionState.Identifying -> "Connecting to OBS…"
            is ConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})…"
            is ConnectionState.Failed -> "Connection failed — tap to open SceneDeck"
            ConnectionState.Disconnected -> "Disconnected"
        }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "OBS session", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun build(context: Context, state: ConnectionState, programScene: String?): Notification {
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
            .setContentText(textFor(state, programScene))
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
