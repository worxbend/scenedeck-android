package com.scenedeck.android.connectivity

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import com.scenedeck.android.core.data.ObsSessionHolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skips the OBS reconnect backoff when retrying is likely to succeed: the app returns to the
 * foreground or a network becomes available. Each signal only nudges an existing backoff wait
 * ([ObsSessionHolder.retryNow]); it never starts, replaces or revives a session.
 */
@Singleton
class ReconnectTriggers @Inject constructor(private val sessionHolder: ObsSessionHolder) {

    private val foreground = ForegroundTransitions(onForeground = { sessionHolder.retryNow() })
    private var started = false

    fun start(application: Application) {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(foreground.callbacks)
        application
            .getSystemService(ConnectivityManager::class.java)
            ?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        sessionHolder.retryNow()
                    }
                }
            )
    }
}

/** Counts started activities and reports background → foreground transitions only. */
internal class ForegroundTransitions(private val onForeground: () -> Unit) {
    private var startedActivities = 0

    fun onActivityStarted() {
        startedActivities++
        if (startedActivities == 1) onForeground()
    }

    fun onActivityStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    // Activity lifecycle callbacks run on the main thread, so the counter needs no locking.
    val callbacks: Application.ActivityLifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = onActivityStarted()

            override fun onActivityStopped(activity: Activity) = onActivityStopped()

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }
}
