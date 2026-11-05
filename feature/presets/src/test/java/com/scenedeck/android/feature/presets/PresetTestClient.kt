package com.scenedeck.android.feature.presets

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsInputSummary
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionMeters
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Typed boundary fixture: no real connection; writes update subsequent confirmation reads. */
internal class PresetTestClient {
    val connection =
        MutableStateFlow<ConnectionState>(
            ConnectionState.Ready(
                ObsVersionInfo(
                    "32",
                    "5",
                    1,
                    "test",
                    ObsCapabilities(controls = ObsControl.entries.toSet()),
                )
            )
        )
    val events = MutableSharedFlow<ObsSessionEvent>()
    private val plainEvents = MutableSharedFlow<ObsEvent>()
    private val meters = MutableSharedFlow<List<VolumeMeterReading>>()
    private val stampedMeters = MutableSharedFlow<ObsSessionMeters>()
    var program = "Other"
    var muted = false
    var stream = false
    var recording = false
    var volume = 1.0
    val writes = mutableListOf<String>()
    var beforeWrite: suspend (String) -> Unit = {}
    var beforeRead: suspend (String) -> Unit = {}

    val client =
        Proxy.newProxyInstance(ObsClient::class.java.classLoader, arrayOf(ObsClient::class.java)) {
            _,
            method,
            args ->
            when (method.name) {
                "getConnectionState" -> connection
                "getEvents" -> plainEvents
                "getEventsWithContext" -> events
                "getVolumeMeters" -> meters
                "getMetersWithContext" -> stampedMeters
                else -> suspended(requireNotNull(args).last()) { request(method.name, args) }
            }
        } as ObsClient

    private suspend fun request(name: String, args: Array<out Any?>): Any {
        if (name.startsWith("get")) {
            beforeRead(name)
            return read(name)
        }
        writes += name
        beforeWrite(name)
        when (name) {
            "setCurrentProgramScene" -> program = args[0] as String
            "setInputMute" -> muted = args[1] as Boolean
            "setInputVolume" -> volume = args[1] as Double
            "startStream" -> stream = true
            "stopStream" -> stream = false
            "startRecord" -> recording = true
            "stopRecord" -> recording = false
            else -> error("Unexpected write $name")
        }
        return Unit
    }

    private fun read(name: String): Any =
        when (name) {
            "getSceneList" ->
                SceneListSnapshot(
                    program,
                    listOf(SceneSummary(" Camera ", 1), SceneSummary("Other", 0)),
                )
            "getInputList" ->
                listOf(
                    ObsInputSummary("Mic", "audio"),
                    ObsInputSummary("Off-program clip", "ffmpeg_source"),
                )
            "getCurrentProgramScene" -> program
            "getInputMute" -> muted
            "getInputVolume" -> volume
            "getStudioModeEnabled" -> false
            "getSpecialInputs" -> SpecialInputs()
            "getSceneItemList" -> emptyList<SceneItemInfo>()
            "getStreamStatus" -> StreamStatus(stream, false, "00:00:00.000", 0, 0, 0.0, 0, 0)
            "getRecordStatus" -> RecordStatus(recording, false, "00:00:00.000", 0, 0)
            "getStats" -> ObsStats(0.0, 0.0, 8_192.0, 30.0, 1.0, 0, 0, 0, 0)
            else -> error("Unsupported read $name")
        }

    @Suppress("UNCHECKED_CAST") // JVM suspend method's last argument is its continuation.
    private fun suspended(argument: Any?, block: suspend () -> Any): Any {
        block.startCoroutine(argument as Continuation<Any>)
        return COROUTINE_SUSPENDED
    }
}
