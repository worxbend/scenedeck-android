package com.scenedeck.android.feature.mixer

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

/** Minimal rig-shaped fake for mixer tests (fixtures can't cross module boundaries). */
@Suppress("TooManyFunctions")
internal class FakeObsClient(
    var sceneListSnapshot: SceneListSnapshot = SceneListSnapshot(null, emptyList()),
) : ObsClient {

    var specialInputs = SpecialInputs(desktop1 = "Desktop Audio", mic1 = "Mic/Aux")
    var sceneItems: Map<String, List<SceneItemInfo>> = emptyMap()
    var mutedInputs = mutableSetOf<String>()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val events: SharedFlow<ObsEvent> = MutableSharedFlow(extraBufferCapacity = 8)
    override val volumeMeters: SharedFlow<List<VolumeMeterReading>> = MutableSharedFlow()

    fun setReady() {
        _connectionState.value = ConnectionState.Ready(ObsVersionInfo("31.0.1", "5.6.1", 1, "test"))
    }

    override suspend fun connect(host: String, port: Int, password: String?) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun getSceneList(): SceneListSnapshot = sceneListSnapshot
    override suspend fun getSpecialInputs(): SpecialInputs = specialInputs
    override suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo> =
        sceneItems[sceneName].orEmpty()

    override suspend fun getInputMute(inputName: String): Boolean = inputName in mutedInputs
    override suspend fun setInputMute(inputName: String, muted: Boolean) {
        if (muted) mutedInputs += inputName else mutedInputs -= inputName
    }

    override suspend fun getInputVolume(inputName: String): Double = 1.0
    override suspend fun setInputVolume(inputName: String, volumeMul: Double) = Unit
    override suspend fun getStats(): ObsStats = error("not needed")
    override suspend fun getStreamStatus(): StreamStatus = error("not needed")
    override suspend fun getRecordStatus(): RecordStatus = error("not needed")
    override suspend fun setCurrentProgramScene(sceneName: String) = Unit


    override suspend fun getStudioModeEnabled(): Boolean = false
    override suspend fun setStudioModeEnabled(enabled: Boolean) = Unit
    override suspend fun getCurrentPreviewScene(): String = unused()
    override suspend fun setCurrentPreviewScene(sceneName: String) = unused()
    override suspend fun triggerStudioModeTransition() = unused()
    override suspend fun getSceneTransitionList() = unused()
    override suspend fun getCurrentSceneTransition() = unused()
    override suspend fun setCurrentSceneTransition(transitionName: String) = unused()
    override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = unused()
    override suspend fun getSourceScreenshot(
        sourceName: String,
        format: String,
        compressionQuality: Int,
        width: Int?,
        height: Int?,
    ): ByteArray = unused()

    private fun unused(): Nothing = throw NotImplementedError("not needed by these tests")

    override suspend fun getVersion() = unused()
    override suspend fun getCurrentProgramScene(): String = unused()
    override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) = unused()
    override suspend fun startStream() = unused()
    override suspend fun stopStream() = unused()
    override suspend fun startRecord() = unused()
    override suspend fun stopRecord() = unused()
    override suspend fun getProfileList() = unused()
    override suspend fun setCurrentProfile(profileName: String) = unused()
    override suspend fun getSceneCollectionList() = unused()
    override suspend fun setCurrentSceneCollection(collectionName: String) = unused()
}
