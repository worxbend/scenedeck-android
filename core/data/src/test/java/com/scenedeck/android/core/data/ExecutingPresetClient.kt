package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary

internal class ExecutingPresetClient : FakeObsClient() {
    val calls = mutableListOf<String>()
    private var program = "Other"
    private var muted = false
    private var volume = 1.0
    private var media = MediaStateKind.PAUSED

    init {
        setReady(capabilities = ObsCapabilities(controls = ObsControl.entries.toSet()))
        sceneListSnapshot =
            SceneListSnapshot(program, listOf(SceneSummary("Camera", 1), SceneSummary("Other", 0)))
        previewScene = "Other"
        studioModeEnabled = true
    }

    override suspend fun getCurrentProgramScene() = program

    override suspend fun setCurrentProgramScene(sceneName: String) {
        calls += "program:$sceneName"
        program = sceneName
    }

    override suspend fun setCurrentPreviewScene(sceneName: String) {
        calls += "preview:$sceneName"
        previewScene = sceneName
    }

    override suspend fun getInputMute(inputName: String) = muted

    override suspend fun setInputMute(inputName: String, muted: Boolean) {
        calls += "mute:$muted"
        this.muted = muted
    }

    override suspend fun getInputVolume(inputName: String) = volume

    override suspend fun setInputVolume(inputName: String, volumeMul: Double) {
        calls += "volume:$volumeMul"
        volume = volumeMul
    }

    override suspend fun getMediaInputStatus(inputName: String) = MediaStatus(media, 10_000, 1_000)

    override suspend fun triggerMediaInputAction(inputName: String, action: MediaActionKind) {
        calls += "media:$action"
        media =
            when (action) {
                MediaActionKind.PLAY -> MediaStateKind.PLAYING
                MediaActionKind.PAUSE -> MediaStateKind.PAUSED
                MediaActionKind.STOP -> MediaStateKind.STOPPED
                else -> error("Preset must use target values")
            }
    }

    override suspend fun startStream() {
        calls += "stream:true"
        streamStatusResponse = streamStatusResponse.copy(active = true)
    }

    override suspend fun stopStream() {
        calls += "stream:false"
        streamStatusResponse = streamStatusResponse.copy(active = false)
    }

    override suspend fun startRecord() {
        calls += "record:true"
        recordStatusResponse = recordStatusResponse.copy(active = true)
    }

    override suspend fun stopRecord() {
        calls += "record:false"
        recordStatusResponse = recordStatusResponse.copy(active = false)
    }
}
