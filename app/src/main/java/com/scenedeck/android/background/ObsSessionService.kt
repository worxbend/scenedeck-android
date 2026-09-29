package com.scenedeck.android.background

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.scenedeck.android.core.data.ObsSessionHolder
import com.scenedeck.android.core.data.ObsStateRepository
import com.scenedeck.android.core.data.ProfileRepository
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.model.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keep-alive foreground service (type `dataSync`): holds the OBS session while the
 * app is backgrounded. START_STICKY — after process death the system restarts it and
 * it reconnects to the last-used profile (ObsClient's backoff loop takes it from
 * there). Stopping (toggle off, notification Disconnect, or a Disconnected state
 * after a Ready session) always disconnects the session.
 *
 * Note: Android 15 caps `dataSync` at 6 h per 24 h window; [onTimeout] shuts down
 * gracefully (disconnect + remove notification).
 */
@AndroidEntryPoint
class ObsSessionService : Service() {

    @Inject lateinit var sessionHolder: ObsSessionHolder
    @Inject lateinit var deck: ObsStateRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var profiles: ProfileRepository

    private val logic = SessionServiceLogic()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stopping = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        SessionNotification.ensureChannel(this)
        scope.launch {
            combine(
                sessionHolder.connectionState,
                deck.deckState,
            ) { connection, deckState -> connection to deckState.currentProgramScene }
                .collect { (connection, program) ->
                    if (logic.onConnectionState(connection) == SessionServiceLogic.Effect.SHUTDOWN) {
                        shutdown()
                    } else {
                        updateNotification(connection, program)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        when (logic.onStartAction(intent?.action)) {
            SessionServiceLogic.Effect.SHUTDOWN -> {
                shutdown()
                START_NOT_STICKY
            }

            SessionServiceLogic.Effect.GO_FOREGROUND -> {
                goForeground()
                connectLastUsed()
                START_STICKY
            }

            SessionServiceLogic.Effect.NONE -> START_STICKY
        }

    override fun onBind(intent: Intent?): IBinder? = null

    /** dataSync 6-hour budget exhausted (API 35+): disconnect and remove ourselves. */
    override fun onTimeout(startId: Int) {
        shutdown()
    }

    override fun onDestroy() {
        scope.cancel()
        sessionHolder.disconnect()
        NotificationManagerCompat.from(this).cancel(SessionNotification.NOTIFICATION_ID)
        super.onDestroy()
    }

    @Suppress("SwallowedException") // best-effort: failure means "not foreground", we stopSelf
    private fun goForeground() {
        val notification = SessionNotification.build(
            this,
            sessionHolder.connectionState.value,
            deck.deckState.value.currentProgramScene,
        )
        try {
            ServiceCompat.startForeground(
                this,
                SessionNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Background-start restriction hit (shouldn't happen: starts are UI-driven).
            stopSelf()
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun connectLastUsed() {
        scope.launch {
            val connectable = when (sessionHolder.connectionState.value) {
                is ConnectionState.Disconnected, is ConnectionState.Failed -> true
                else -> false
            }
            if (!connectable) return@launch
            val profileId = settings.settings.first().lastUsedProfileId
                ?: profiles.lastUsed()?.id
                ?: return@launch
            sessionHolder.connect(profileId)
        }
    }

    private fun updateNotification(state: ConnectionState, program: String?) {
        if (stopping.get()) return
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(SessionNotification.NOTIFICATION_ID, SessionNotification.build(this, state, program))
        }
    }

    private fun shutdown() {
        if (!stopping.compareAndSet(false, true)) return
        sessionHolder.disconnect()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Explicit cancel: DeckState emissions racing the disconnect would otherwise
        // re-post the (no-longer-foreground) notification after REMOVE.
        NotificationManagerCompat.from(this).cancel(SessionNotification.NOTIFICATION_ID)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.scenedeck.android.action.SESSION_START"
        const val ACTION_DISCONNECT = "com.scenedeck.android.action.SESSION_DISCONNECT"

        fun intent(context: Context, action: String): Intent =
            Intent(context, ObsSessionService::class.java).setAction(action)
    }
}
