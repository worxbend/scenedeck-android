package com.scenedeck.android.feature.stats

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal rig-shaped fake for stats tests (fixtures can't cross module boundaries). */
@Suppress("TooManyFunctions")
internal class FakeObsClient(
    var statsResponse: ObsStats = ObsStats(
        cpuUsage = 0.0,
        memoryUsageMb = 0.0,
        availableDiskSpaceMb = 0.0,
        activeFps = 0.0,
        averageFrameRenderTimeMs = 0.0,
        renderSkippedFrames = 0,
        renderTotalFrames = 0,
        outputSkippedFrames = 0,
        outputTotalFrames = 0,
    ),
    var streamStatusResponse: StreamStatus = StreamStatus(
        active = false,
        reconnecting = false,
        timecode = "00:00:00.000",
        durationMs = 0,
        bytes = 0,
        congestion = 0.0,
        skippedFrames = 0,
        totalFrames = 0,
    ),
    var recordStatusResponse: RecordStatus = RecordStatus(
        active = false,
        paused = false,
        timecode = "00:00:00.000",
        durationMs = 0,
        bytes = 0,
    ),
) : ObsClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val events: SharedFlow<ObsEvent> = MutableSharedFlow(extraBufferCapacity = 8)
    override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

    fun setReady() {
        _connectionState.value = ConnectionState.Ready(ObsVersionInfo("31.0.1", "5.6.1", 1, "test"))
    }

    fun setDisconnected() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun connect(host: String, port: Int, password: String?) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun getStats(): ObsStats = statsResponse
    override suspend fun getStreamStatus(): StreamStatus = streamStatusResponse
    override suspend fun getRecordStatus(): RecordStatus = recordStatusResponse

    private fun unused(): Nothing = throw NotImplementedError("not needed by these tests")

    override suspend fun getVersion() = unused()
    override suspend fun getSceneList(): SceneListSnapshot = unused()
    override suspend fun getCurrentProgramScene(): String = unused()
    override suspend fun setCurrentProgramScene(sceneName: String) = unused()
    override suspend fun getSceneItemList(sceneName: String) = unused()
    override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = unused()
    override suspend fun getSpecialInputs() = unused()
    override suspend fun getInputMute(inputName: String) = unused()
    override suspend fun setInputMute(inputName: String, muted: Boolean) = unused()
    override suspend fun getInputVolume(inputName: String) = unused()
    override suspend fun setInputVolume(inputName: String, volumeMul: Double) = unused()
    override suspend fun startStream() = unused()
    override suspend fun stopStream() = unused()
    override suspend fun startRecord() = unused()
    override suspend fun stopRecord() = unused()
    override suspend fun getProfileList() = unused()
    override suspend fun setCurrentProfile(profileName: String) = unused()
    override suspend fun getSceneCollectionList() = unused()
    override suspend fun setCurrentSceneCollection(collectionName: String) = unused()
}
