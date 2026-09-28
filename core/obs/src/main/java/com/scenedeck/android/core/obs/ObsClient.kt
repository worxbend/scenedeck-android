package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
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
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.internal.KtobsObsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single entry point to OBS (docs/ARCHITECTURE.md rule 1). Implementation is
 * ktobs/Ktor-based but no ktobs/Ktor type escapes this API (see core/obs/README.md).
 *
 * Typical use: `connect(...)` → collect [connectionState]/[events]/[volumeMeters] →
 * call suspend requests while [ConnectionState.Ready].
 */
@Suppress("TooManyFunctions") // the OBS request surface is intentionally broad
interface ObsClient {

    /** Session state machine: Disconnected → Connecting → Identifying → Ready → Reconnecting. */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Domain events pushed by OBS (scene/input/output/config lifecycle). Buffered;
     * on overflow the oldest events are dropped — never blocks the protocol lane.
     */
    val events: SharedFlow<ObsEvent>

    /**
     * High-volume (~50 ms) meter batches. Dedicated flow with a small buffer and
     * [kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST] (desktop parity).
     */
    val volumeMeters: SharedFlow<List<VolumeMeterReading>>

    /**
     * Connects and authenticates. Suspends until the session is [ConnectionState.Ready]
     * or the first attempt ends in [ConnectionState.Failed]. On unexpected socket loss
     * the client reconnects automatically with exponential backoff; auth failures are
     * terminal ([ConnectionError.Auth], no retry).
     */
    suspend fun connect(host: String, port: Int = ObsProfile.DEFAULT_PORT, password: String? = null)

    /** Ends the session and stops any reconnect loop. State becomes [ConnectionState.Disconnected]. */
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
class ObsRequestFailedException(
    val requestType: String,
    val statusCode: String,
    message: String?,
    cause: Throwable? = null,
) : Exception("Request '$requestType' failed ($statusCode): $message", cause)
