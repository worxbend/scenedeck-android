package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Scriptable [ObsClient] fake for repository tests. */
@Suppress("TooManyFunctions")
internal open class FakeObsClient(
    var sceneListSnapshot: SceneListSnapshot = SceneListSnapshot(null, emptyList()),
    var statsResponse: ObsStats = ObsStats(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0),
    var streamStatusResponse: StreamStatus =
        StreamStatus(false, false, "00:00:00.000", 0, 0, 0.0, 0, 0),
    var recordStatusResponse: RecordStatus = RecordStatus(false, false, "00:00:00.000", 0, 0),
) : ObsClient {

    var studioModeEnabled = false
    var previewScene = ""

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<ObsEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<ObsEvent> = _events

    override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

    val sceneSwitchCalls = mutableListOf<String>()

    fun setReady() {
        _connectionState.value = ConnectionState.Ready(ObsVersionInfo("31.0.1", "5.6.1", 1, "test"))
    }

    fun setDisconnected() {
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun emit(event: ObsEvent) = _events.emit(event)

    override suspend fun connect(host: String, port: Int, password: String?) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun getSceneList(): SceneListSnapshot = sceneListSnapshot

    override suspend fun setCurrentProgramScene(sceneName: String) {
        sceneSwitchCalls += sceneName
    }

    override suspend fun getStats(): ObsStats = statsResponse

    override suspend fun getStreamStatus(): StreamStatus = streamStatusResponse

    override suspend fun getRecordStatus(): RecordStatus = recordStatusResponse

    // ── Studio mode & transitions (M6) ──────────────────────────────────────

    open override suspend fun getStudioModeEnabled(): Boolean = studioModeEnabled

    open override suspend fun setStudioModeEnabled(enabled: Boolean) {
        studioModeEnabled = enabled
    }

    open override suspend fun getCurrentPreviewScene(): String = previewScene

    open override suspend fun setCurrentPreviewScene(sceneName: String) {
        previewScene = sceneName
    }

    open override suspend fun triggerStudioModeTransition() = Unit

    open override suspend fun getSceneTransitionList() = unused()

    open override suspend fun getCurrentSceneTransition() = unused()

    open override suspend fun setCurrentSceneTransition(transitionName: String) = Unit

    open override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = Unit

    val screenshotCalls = mutableListOf<String>()
    var screenshotBytes: ByteArray = ByteArray(0)

    open override suspend fun getSourceScreenshot(
        sourceName: String,
        format: String,
        compressionQuality: Int,
        width: Int?,
        height: Int?,
    ): ByteArray {
        screenshotCalls += sourceName
        return screenshotBytes
    }

    private fun unused(): Nothing = throw UnsupportedOperationException("not needed by these tests")

    override suspend fun getVersion() = unused()

    override suspend fun getCurrentProgramScene(): String = unused()

    open override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> = unused()

    open override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int): Boolean =
        unused()

    open override suspend fun getSpecialInputs(): SpecialInputs = unused()

    open override suspend fun getInputMute(inputName: String): Boolean = unused()

    open override suspend fun setInputMute(inputName: String, muted: Boolean): Unit = unused()

    open override suspend fun getInputVolume(inputName: String): Double = unused()

    open override suspend fun setInputVolume(inputName: String, volumeMul: Double): Unit = unused()

    override suspend fun startStream() = unused()

    override suspend fun stopStream() = unused()

    override suspend fun startRecord() = unused()

    override suspend fun stopRecord() = unused()

    override suspend fun getProfileList() = unused()

    override suspend fun setCurrentProfile(profileName: String) = unused()

    override suspend fun getSceneCollectionList() = unused()

    override suspend fun setCurrentSceneCollection(collectionName: String) = unused()
}
