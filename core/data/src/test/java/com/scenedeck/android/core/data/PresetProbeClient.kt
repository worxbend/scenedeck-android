package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary

internal class PresetProbeClient : FakeObsClient() {
    val reads = mutableListOf<String>()
    var beforeRead: suspend (String) -> Unit = {}
    var volume = 0.25
    var muted = false
    var media = MediaStateKind.PAUSED

    init {
        setReady(capabilities = ObsCapabilities(controls = ObsControl.entries.toSet()))
        sceneListSnapshot = SceneListSnapshot("Camera", listOf(SceneSummary("Camera", 0)))
        previewScene = "Camera"
    }

    override suspend fun getCurrentProgramScene() = "Camera"

    override suspend fun getInputMute(inputName: String): Boolean {
        reads += inputName
        beforeRead(inputName)
        return muted
    }

    override suspend fun getInputVolume(inputName: String): Double {
        reads += inputName
        beforeRead(inputName)
        return volume
    }

    override suspend fun getMediaInputStatus(inputName: String): MediaStatus {
        reads += inputName
        beforeRead(inputName)
        return MediaStatus(media, 10_000, 1_000)
    }
}
