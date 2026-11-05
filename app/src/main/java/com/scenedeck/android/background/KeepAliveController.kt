package com.scenedeck.android.background

import android.content.Context
import androidx.core.content.ContextCompat
import com.scenedeck.android.demo.AppProcess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single entry point for the keep-alive switch: persists the flag (app-private DataStore) and
 * starts/stops [ObsSessionService]. Starts are best-effort — the system may refuse a
 * foreground-service start, in which case the flag still applies on next app launch / STICKY
 * restart.
 */
@Singleton
class KeepAliveController
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val store: BackgroundSettingsStore,
    private val process: AppProcess,
) {
    val keepAliveEnabled: Flow<Boolean> = store.settings.map { it.keepAliveEnabled }

    suspend fun setEnabled(enabled: Boolean) {
        if (process != AppProcess.LIVE) return
        store.setKeepAliveEnabled(enabled)
        if (enabled) start() else stop()
    }

    /** (Re)starts the service; call from a foreground context (activity/sheet). */
    @Suppress("SwallowedException") // best-effort start; next foreground launch retries
    fun start(): Boolean {
        if (process != AppProcess.LIVE) return false
        return try {
            ContextCompat.startForegroundService(
                context,
                ObsSessionService.intent(context, ObsSessionService.ACTION_START),
            )
            true
        } catch (e: IllegalStateException) {
            // Background start restrictions on older API levels.
            false
        }
    }

    fun stop() {
        if (process != AppProcess.LIVE) return
        context.stopService(ObsSessionService.intent(context, ObsSessionService.ACTION_DISCONNECT))
    }
}
