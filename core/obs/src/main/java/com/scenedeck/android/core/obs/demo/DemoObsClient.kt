package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsCapabilities
import com.scenedeck.android.core.model.ObsControl
import com.scenedeck.android.core.model.ObsProfile
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.ObsVideoSettings
import com.scenedeck.android.core.model.ProfileListSnapshot
import com.scenedeck.android.core.model.SceneCollectionListSnapshot
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ScreenshotRequest
import kotlinx.coroutines.CoroutineScope

/**
 * Local simulation behind the normal typed boundary. No socket, client factory, file, camera or
 * credential dependency. The app must supply an isolated data graph and unmistakable demo chrome.
 */
@Suppress("TooManyFunctions") // Implements the same deliberately broad typed ObsClient boundary.
class DemoObsClient(
    scope: CoroutineScope,
    elapsedClock: () -> Long,
    private val screenshot: ((ScreenshotRequest) -> ByteArray)? = null,
) : ObsClient {
    private val session = DemoSession(scope, elapsedClock)
    override val connectionState = session.state
    override val events = session.events
    override val volumeMeters = session.meters
    override val eventsWithContext = session.contextEvents
    override val metersWithContext = session.contextMeters

    override suspend fun connect(host: String, port: Int, password: String?) =
        session.connect(host == HOST && port == ObsProfile.DEFAULT_PORT && password == null)

    override suspend fun disconnect() = session.disconnect()

    override suspend fun getVersion() = session.request { VERSION }

    override suspend fun getStats() = session.request {
        val frames = (elapsed() / 1_000 * 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        ObsStats(8.4, 384.0, 120_000.0, 60.0, 3.2, 0, frames, 0, frames)
    }

    override suspend fun getVideoSettings() = session.request {
        ObsVideoSettings(60, 1, 1920, 1080, 1920, 1080)
    }

    override suspend fun getSceneList() = session.request { studio.scenes() }

    override suspend fun getCurrentProgramScene() = session.request { studio.program }

    override suspend fun setCurrentProgramScene(sceneName: String) = session.request {
        studio.program(sceneName)
    }

    override suspend fun getSceneItemList(sceneName: String) = session.request {
        studio.items(sceneName)
    }

    override suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int) =
        session.request {
            studio.item(sceneName, sceneItemId).enabled
        }

    override suspend fun setSceneItemEnabled(
        sceneName: String,
        sceneItemId: Int,
        enabled: Boolean,
    ) = session.request { studio.itemEnabled(sceneName, sceneItemId, enabled) }

    override suspend fun getSpecialInputs() = session.request {
        SpecialInputs(desktop1 = DemoCatalog.MUSIC, mic1 = DemoCatalog.MIC)
    }

    override suspend fun getInputList() = session.request { DemoCatalog.inputs }

    override suspend fun getInputMute(inputName: String) = session.request {
        audio.input(inputName).muted
    }

    override suspend fun setInputMute(inputName: String, muted: Boolean) = session.request {
        audio.mute(inputName, muted)
    }

    override suspend fun getInputVolume(inputName: String) = session.request {
        audio.input(inputName).volume
    }

    override suspend fun setInputVolume(inputName: String, volumeMul: Double) = session.request {
        audio.volume(inputName, volumeMul)
    }

    override suspend fun getStreamStatus() = session.request { outputs.streamStatus() }

    override suspend fun startStream() = session.request { outputs.stream(true) }

    override suspend fun stopStream() = session.request { outputs.stream(false) }

    override suspend fun getRecordStatus() = session.request { outputs.recordStatus() }

    override suspend fun startRecord() = session.request { outputs.record(true) }

    override suspend fun stopRecord() = session.request { outputs.record(false) }

    override suspend fun pauseRecord() = session.request { outputs.paused(true) }

    override suspend fun resumeRecord() = session.request { outputs.paused(false) }

    override suspend fun splitRecordFile() = session.request { outputs.advancedRecording() }

    override suspend fun createRecordChapter(chapterName: String?) = session.request {
        demoRequire(chapterName == null || chapterName.length <= 1_024)
        outputs.advancedRecording()
    }

    override suspend fun getStudioModeEnabled() = session.request { studio.enabled }

    override suspend fun setStudioModeEnabled(enabled: Boolean) = session.request {
        studio.enabled(enabled)
    }

    override suspend fun getCurrentPreviewScene() = session.request { studio.preview() }

    override suspend fun setCurrentPreviewScene(sceneName: String) = session.request {
        studio.preview(sceneName)
    }

    override suspend fun triggerStudioModeTransition() = session.request { studio.take() }

    override suspend fun getSceneTransitionList() = session.request { studio.transitions.list() }

    override suspend fun getCurrentSceneTransition() = session.request {
        studio.transitions.current()
    }

    override suspend fun setCurrentSceneTransition(transitionName: String) = session.request {
        studio.transitions.select(transitionName)
    }

    override suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = session.request {
        studio.transitions.duration(durationMs)
    }

    override suspend fun getSourceScreenshot(request: ScreenshotRequest) = session.request {
        DemoCatalog.requireScene(request.sourceName)
        val capture = screenshot
        demoRequire(capture != null)
        val bytes = requireNotNull(capture)(request)
        demoRequire(bytes.isNotEmpty() && bytes.size <= ScreenshotRequest.MAX_IMAGE_BYTES)
        bytes.copyOf()
    }

    override suspend fun getVirtualCamStatus() = session.request { outputs.camera }

    override suspend fun toggleVirtualCam() = session.request { outputs.toggleCamera() }

    override suspend fun getMediaInputStatus(inputName: String) = session.request {
        media.status(inputName)
    }

    override suspend fun setMediaInputCursor(inputName: String, cursorMs: Long) = session.request {
        media.seek(inputName, cursorMs)
    }

    override suspend fun triggerMediaInputAction(inputName: String, action: MediaActionKind) =
        session.request {
            media.action(inputName, action)
        }

    override suspend fun getInputAudioBalance(inputName: String) = session.request {
        audio.input(inputName).balance
    }

    override suspend fun setInputAudioBalance(inputName: String, balance: Double) =
        session.request {
            audio.balance(inputName, balance)
        }

    override suspend fun getInputAudioSyncOffset(inputName: String) = session.request {
        audio.input(inputName).syncOffsetMs
    }

    override suspend fun setInputAudioSyncOffset(inputName: String, offsetMs: Int) =
        session.request {
            audio.sync(inputName, offsetMs)
        }

    override suspend fun getInputAudioMonitorType(inputName: String) = session.request {
        audio.input(inputName).monitor
    }

    override suspend fun setInputAudioMonitorType(inputName: String, monitorType: MonitorTypeKind) =
        session.request {
            audio.monitor(inputName, monitorType)
        }

    override suspend fun getProfileList() = session.request {
        ProfileListSnapshot(DemoCatalog.PROFILE, listOf(DemoCatalog.PROFILE))
    }

    override suspend fun setCurrentProfile(profileName: String) = session.request {
        demoRequire(profileName == DemoCatalog.PROFILE, missing = true)
    }

    override suspend fun getSceneCollectionList() = session.request {
        SceneCollectionListSnapshot(DemoCatalog.COLLECTION, listOf(DemoCatalog.COLLECTION))
    }

    override suspend fun setCurrentSceneCollection(collectionName: String) = session.request {
        demoRequire(collectionName == DemoCatalog.COLLECTION, missing = true)
    }

    companion object {
        const val HOST = "local-demo"
        internal val VERSION =
            ObsVersionInfo(
                "Demo",
                "5 (simulated)",
                1,
                "Local simulation",
                ObsCapabilities(
                    pauseRecording = true,
                    resumeRecording = true,
                    splitRecording = true,
                    recordingChapter = true,
                    mediaSeek = true,
                    controls = ObsControl.entries.toSet(),
                ),
            )
    }
}
