package com.scenedeck.android.feature.preflight

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Only reads are implemented; any accidental OBS write fails loudly. */
internal class PreflightTestClient {
    val connection =
        MutableStateFlow<ConnectionState>(
            ConnectionState.Ready(ObsVersionInfo("32", "5", 1, "test"))
        )
    private val events = MutableSharedFlow<ObsEvent>()
    private val contextEvents = MutableSharedFlow<ObsSessionEvent>()
    private val meters = MutableSharedFlow<List<VolumeMeterReading>>()
    private val contextMeters = MutableSharedFlow<ObsSessionMeters>()
    var statsGate: CompletableDeferred<Unit>? = null
    var failStats = false
    var statsCalls = 0
    private val stats = ObsStats(0.0, 0.0, 8_192.0, 30.0, 1.0, 0, 0, 0, 0)

    val client =
        Proxy.newProxyInstance(ObsClient::class.java.classLoader, arrayOf(ObsClient::class.java)) {
            _,
            method,
            args ->
            when (method.name) {
                "getConnectionState" -> connection
                "getEvents" -> events
                "getEventsWithContext" -> contextEvents
                "getVolumeMeters" -> meters
                "getMetersWithContext" -> contextMeters
                "getSceneList" -> SceneListSnapshot("Camera", listOf(SceneSummary("Camera", 0)))
                "getStudioModeEnabled" -> false
                "getInputMute" -> false
                "getStats" ->
                    suspendedRead(requireNotNull(args).last()) {
                        statsCalls++
                        statsGate?.await()
                        if (failStats) error("private server details")
                        stats
                    }
                else -> error("Unexpected request: ${method.name}")
            }
        } as ObsClient

    @Suppress("UNCHECKED_CAST") // JVM suspend method's final argument is its continuation.
    private fun suspendedRead(argument: Any?, read: suspend () -> Any): Any {
        read.startCoroutine(argument as Continuation<Any>)
        return COROUTINE_SUSPENDED
    }
}
