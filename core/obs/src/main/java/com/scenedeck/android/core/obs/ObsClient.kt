package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsProfile
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.ProfileListSnapshot
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.SceneCollectionListSnapshot
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.TransitionListSnapshot
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.internal.KtobsObsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single entry point to OBS (docs/ARCHITECTURE.md rule 1). Implementation is ktobs/Ktor-based
 * but no ktobs/Ktor type escapes this API (see core/obs/README.md).
 *
 * Typical use: `connect(...)` → collect [connectionState]/[events]/[volumeMeters] → call suspend
 * requests while [ConnectionState.Ready].
 */
@Suppress("TooManyFunctions") // the OBS request surface is intentionally broad
interface ObsClient {

    /** Session state machine: Disconnected → Connecting → Identifying → Ready → Reconnecting. */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Domain events pushed by OBS (scene/input/output/config lifecycle). Buffered; on overflow the
     * oldest events are dropped — never blocks the protocol lane.
     */
    val events: SharedFlow<ObsEvent>

    /**
     * High-volume (~50 ms) meter batches. Dedicated flow with a small buffer and
     * [kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST] (desktop parity).
     */
    val volumeMeters: SharedFlow<List<VolumeMeterReading>>

    /**
     * Connects and authenticates. Suspends until the session is [ConnectionState.Ready] or the
     * first attempt ends in [ConnectionState.Failed]. On unexpected socket loss the client
     * reconnects automatically with exponential backoff; auth failures are terminal
     * ([ConnectionError.Auth], no retry).
     */
    suspend fun connect(host: String, port: Int = ObsProfile.DEFAULT_PORT, password: String? = null)

    /**
     * Ends the session and stops any reconnect loop. State becomes [ConnectionState.Disconnected].
     */
    suspend fun disconnect()

    // ── General ─────────────────────────────────────────────────────────────
    suspend fun getVersion(): ObsVersionInfo

    suspend fun getStats(): ObsStats

    // ── Scenes ──────────────────────────────────────────────────────────────
    suspend fun getSceneList(): SceneListSnapshot

    suspend fun getCurrentProgramScene(): String

    suspend fun setCurrentProgramScene(sceneName: String)

    suspend fun getSceneItemList(sceneName: String): List<SceneItemInfo>

    suspend fun getSceneItemEnabled(sceneName: String, sceneItemId: Int): Boolean

    // ── Inputs / audio ──────────────────────────────────────────────────────
    suspend fun getSpecialInputs(): SpecialInputs

    suspend fun getInputMute(inputName: String): Boolean

    suspend fun setInputMute(inputName: String, muted: Boolean)

    /** Current volume as a linear multiplier (OBS `inputVolumeMul`). */
    suspend fun getInputVolume(inputName: String): Double

    /** Sets volume as a linear multiplier (OBS `inputVolumeMul`). */
    suspend fun setInputVolume(inputName: String, volumeMul: Double)

    // ── Outputs ─────────────────────────────────────────────────────────────
    suspend fun getStreamStatus(): StreamStatus

    suspend fun startStream()

    suspend fun stopStream()

    suspend fun getRecordStatus(): RecordStatus

    suspend fun startRecord()

    suspend fun stopRecord()

    // ── Studio mode & transitions (M6) ──────────────────────────────────────
    // Default bodies keep pre-M6 fakes compiling (they fail loudly if called).

    suspend fun getStudioModeEnabled(): Boolean = error(NOT_IMPLEMENTED)

    suspend fun setStudioModeEnabled(enabled: Boolean) = Unit

    suspend fun getCurrentPreviewScene(): String = error(NOT_IMPLEMENTED)

    suspend fun setCurrentPreviewScene(sceneName: String) = Unit

    /** Commits preview → program with the current transition. */
    suspend fun triggerStudioModeTransition() = Unit

    suspend fun getSceneTransitionList(): TransitionListSnapshot = error(NOT_IMPLEMENTED)

    suspend fun getCurrentSceneTransition(): CurrentTransition = error(NOT_IMPLEMENTED)

    suspend fun setCurrentSceneTransition(transitionName: String) = Unit

    suspend fun setCurrentSceneTransitionDuration(durationMs: Int) = Unit

    /**
     * Scene screenshot (OBS `GetSourceScreenshot`) as encoded image bytes (JPEG when [format] is
     * "jpeg"). Throws [ObsRequestFailedException] on sources that can't be captured.
     */
    suspend fun getSourceScreenshot(
        sourceName: String,
        format: String = "jpeg",
        compressionQuality: Int = 50,
        width: Int? = 360,
        height: Int? = null,
    ): ByteArray = error(NOT_IMPLEMENTED)

    // ── Power features (M7) — default bodies keep pre-M7 fakes compiling ──────

    suspend fun getVirtualCamStatus(): Boolean = error(NOT_IMPLEMENTED)

    /** Returns the new active state. */
    suspend fun toggleVirtualCam(): Boolean = error(NOT_IMPLEMENTED)

    suspend fun getReplayBufferStatus(): Boolean = error(NOT_IMPLEMENTED)

    /** Returns the new active state. */
    suspend fun toggleReplayBuffer(): Boolean = error(NOT_IMPLEMENTED)

    suspend fun saveReplayBuffer() = Unit

    /** Filesystem path of the last saved replay. */
    suspend fun getLastReplayBufferReplay(): String = error(NOT_IMPLEMENTED)

    suspend fun getMediaInputStatus(inputName: String): MediaStatus = error(NOT_IMPLEMENTED)

    suspend fun setMediaInputCursor(inputName: String, cursorMs: Long) = Unit

    suspend fun triggerMediaInputAction(inputName: String, action: MediaActionKind) = Unit

    // ── Scene items ─────────────────────────────────────────────────────────

    suspend fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean) = Unit

    // ── Audio extras ────────────────────────────────────────────────────────

    suspend fun getInputAudioBalance(inputName: String): Double = error(NOT_IMPLEMENTED)

    suspend fun setInputAudioBalance(inputName: String, balance: Double) = Unit

    suspend fun getInputAudioSyncOffset(inputName: String): Int = error(NOT_IMPLEMENTED)

    suspend fun setInputAudioSyncOffset(inputName: String, offsetMs: Int) = Unit

    suspend fun getInputAudioMonitorType(inputName: String): MonitorTypeKind =
        error(NOT_IMPLEMENTED)

    suspend fun setInputAudioMonitorType(inputName: String, monitorType: MonitorTypeKind) = Unit

    // ── Config ──────────────────────────────────────────────────────────────
    suspend fun getProfileList(): ProfileListSnapshot

    suspend fun setCurrentProfile(profileName: String)

    suspend fun getSceneCollectionList(): SceneCollectionListSnapshot

    suspend fun setCurrentSceneCollection(collectionName: String)

    companion object {
        /** Creates the default ktobs/Ktor-backed client bound to [scope]'s lifetime. */
        operator fun invoke(scope: CoroutineScope): ObsClient = KtobsObsClient(scope)
    }
}

/** Thrown when a request is issued while the session is not [ConnectionState.Ready]. */
class ObsNotConnectedException : IllegalStateException("Not connected to OBS")

/**
 * OBS answered a request with `result: false`. [statusCode] is the obs-websocket
 * `RequestStatus.code` name (e.g. `ResourceNotFound`).
 */
private const val NOT_IMPLEMENTED = "ObsClient member not implemented (added in M6)"

class ObsRequestFailedException(
    val requestType: String,
    val statusCode: String,
    message: String?,
    cause: Throwable? = null,
) : Exception("Request '$requestType' failed ($statusCode): $message", cause)
