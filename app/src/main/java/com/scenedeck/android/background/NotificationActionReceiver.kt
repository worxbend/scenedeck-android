package com.scenedeck.android.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Notification quick actions enter the app process here and are forwarded to
 * [ObsSessionService] as explicit intents (single routing point, unit-testable
 * action constants).
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISCONNECT ->
                context.startService(ObsSessionService.intent(context, ObsSessionService.ACTION_DISCONNECT))
        }
    }

    companion object {
        const val ACTION_DISCONNECT = "com.scenedeck.android.action.NOTIFICATION_DISCONNECT"
    }
}
