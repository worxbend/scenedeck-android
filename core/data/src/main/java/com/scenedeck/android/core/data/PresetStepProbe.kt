package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsRequestFailedException
import com.scenedeck.android.core.obs.isInputWithoutAudio
import com.scenedeck.android.core.obs.isResourceNotFound
import com.scenedeck.android.core.obs.withSession
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/** Finite read checks. No control writes, toggles, retries or scene-collection edits. */
internal class PresetStepProbe(
    private val client: ObsClient,
    private val workspaces: WorkspaceContext,
) {
    suspend fun inspect(
        origin: WorkspaceSession,
        step: PresetStep,
        policy: PresetPolicy,
    ): PresetCheck {
        step.checkPresetStep()
        requireCurrent(origin)
        val blocked = localCheck(step, policy)
        val checked =
            blocked
                ?: withTimeoutOrNull(8_000) {
                    requestResult { inspectTarget(origin, step) }.fold({ it }, ::readFailure)
                }
                ?: PresetCheck.UNKNOWN
        requireCurrent(origin)
        return checked
    }

    private fun localCheck(step: PresetStep, policy: PresetPolicy): PresetCheck? {
        val available =
            (client.connectionState.value as? ConnectionState.Ready)
                ?.sessionInfo
                ?.capabilities
                ?.controls
                .orEmpty()
        return when {
            step.control !in available -> PresetCheck.UNSUPPORTED
            step.input in policy.lockedInputs -> PresetCheck.LOCKED
            step.input != null &&
                policy.allowedInputs.isNotEmpty() &&
                step.input !in policy.allowedInputs -> PresetCheck.NOT_ALLOWED
            else -> null
        }
    }

    private suspend fun inspectTarget(origin: WorkspaceSession, step: PresetStep): PresetCheck =
        when (step) {
            is PresetStep.Scene -> scene(origin, step)
            is PresetStep.Mute ->
                matches(read(origin) { getInputMute(step.inputName) } == step.muted)
            is PresetStep.Volume -> {
                val value = read(origin) { getInputVolume(step.inputName) }
                if (value.isFinite() && value >= 0) matches(volumeMatches(value, step.multiplier))
                else PresetCheck.UNKNOWN
            }
            is PresetStep.Media -> media(origin, step)
            is PresetStep.Output -> output(origin, step)
        }

    private suspend fun scene(origin: WorkspaceSession, step: PresetStep.Scene): PresetCheck {
        val names = read(origin) { getSceneList().scenes.map { it.name } }
        val preview = step.destination == PresetSceneDestination.PREVIEW
        return when {
            step.name !in names -> PresetCheck.MISSING
            preview && !read(origin) { getStudioModeEnabled() } -> PresetCheck.STUDIO_OFF
            else -> {
                val current =
                    if (preview) read(origin) { getCurrentPreviewScene() }
                    else read(origin) { getCurrentProgramScene() }
                if (current.isBlank()) PresetCheck.UNKNOWN else matches(current == step.name)
            }
        }
    }

    private suspend fun media(origin: WorkspaceSession, step: PresetStep.Media): PresetCheck {
        val state = read(origin) { getMediaInputStatus(step.inputName).state }
        val expected =
            when (step.state) {
                PresetMediaState.PLAYING -> MediaStateKind.PLAYING
                PresetMediaState.PAUSED -> MediaStateKind.PAUSED
                PresetMediaState.STOPPED -> MediaStateKind.STOPPED
            }
        return if (
            state in
                setOf(
                    MediaStateKind.NONE,
                    MediaStateKind.ERROR,
                    MediaStateKind.OPENING,
                    MediaStateKind.BUFFERING,
                )
        )
            PresetCheck.NOT_READY
        else matches(state == expected)
    }

    private suspend fun output(origin: WorkspaceSession, step: PresetStep.Output): PresetCheck =
        when (step.output) {
            PresetOutput.STREAM -> {
                val state = read(origin) { getStreamStatus() }
                if (state.reconnecting && step.active) PresetCheck.NOT_READY
                else matches((state.active || state.reconnecting) == step.active)
            }
            PresetOutput.RECORDING -> {
                val state = read(origin) { getRecordStatus() }
                if (state.paused && !state.active) PresetCheck.UNKNOWN
                else matches(state.active == step.active)
            }
        }

    private suspend fun <T> read(
        origin: WorkspaceSession,
        operation: suspend ObsClient.() -> T,
    ): T {
        requireCurrent(origin)
        val value = client.withSession(origin.token) { operation(client) }
        requireCurrent(origin)
        return value
    }

    private fun requireCurrent(origin: WorkspaceSession) {
        workspaces.requireCurrent(origin)
        val ready = client.connectionState.value as? ConnectionState.Ready
        if (ready?.token != origin.token || ready.collectionChanging)
            throw WorkspaceChangedException()
    }
}

private fun matches(value: Boolean) =
    if (value) PresetCheck.CURRENTLY_MATCHES else PresetCheck.READY

private fun volumeMatches(actual: Double, expected: Double): Boolean =
    if (expected == 0.0) actual == 0.0
    else abs(actual - expected) <= maxOf(0.000000001, expected * 0.001)

private fun readFailure(error: Throwable): PresetCheck =
    when {
        error is WorkspaceChangedException -> throw error
        error is ObsRequestFailedException && error.isResourceNotFound -> PresetCheck.MISSING
        error is ObsRequestFailedException && error.isInputWithoutAudio -> PresetCheck.NOT_READY
        else -> PresetCheck.UNKNOWN
    }
