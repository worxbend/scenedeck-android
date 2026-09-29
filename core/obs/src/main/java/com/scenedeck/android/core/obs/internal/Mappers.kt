@file:Suppress("TooManyFunctions") // one mapper per response type is the point of this file

package com.scenedeck.android.core.obs.internal

/** ktobs response/model types → :core:model domain types. */

import com.rejeq.ktobs.model.SceneItem
import com.rejeq.ktobs.request.config.GetProfileListResponse
import com.rejeq.ktobs.request.config.GetSceneCollectionListResponse
import com.rejeq.ktobs.request.general.GetStatsResponse
import com.rejeq.ktobs.request.general.GetVersionResponse
import com.rejeq.ktobs.request.inputs.GetSpecialInputsResponse
import com.rejeq.ktobs.request.record.GetRecordStatusResponse
import com.rejeq.ktobs.request.scenes.GetCurrentPreviewSceneResponse
import com.rejeq.ktobs.request.scenes.GetCurrentProgramSceneResponse
import com.rejeq.ktobs.request.scenes.GetSceneListResponse
import com.rejeq.ktobs.request.stream.GetStreamStatusResponse
import com.rejeq.ktobs.request.transitions.GetCurrentSceneTransitionResponse
import com.rejeq.ktobs.request.transitions.GetSceneTransitionListResponse
import com.scenedeck.android.core.model.ObsStats
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.ProfileListSnapshot
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.SceneCollectionListSnapshot
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.core.model.TransitionInfo
import com.scenedeck.android.core.model.TransitionListSnapshot


internal fun GetVersionResponse.toDomain() = ObsVersionInfo(
    obsVersion = obsVersion,
    obsWebSocketVersion = obsWebSocketVersion,
    rpcVersion = rpcVersion,
    platform = platform,
)

internal fun GetSceneListResponse.toDomain() = SceneListSnapshot(
    currentProgramScene = programName,
    scenes = scenes.map { SceneSummary(name = it.name, index = it.index) },
)

internal fun GetCurrentProgramSceneResponse.toDomain(): String = currProgramName ?: name

internal fun GetCurrentPreviewSceneResponse.toDomain(): String = (currPreviewName ?: name).orEmpty()

internal fun SceneItem.toDomain() = SceneItemInfo(
    id = id,
    index = index,
    sourceName = sourceName,
    enabled = enabled,
    isGroup = isGroup == true,
    inputKind = inputKind,
)

internal fun GetSpecialInputsResponse.toDomain() = SpecialInputs(
    desktop1 = desktop1,
    desktop2 = desktop2,
    mic1 = mic1,
    mic2 = mic2,
    mic3 = mic3,
    mic4 = mic4,
)

internal fun GetStreamStatusResponse.toDomain() = StreamStatus(
    active = active,
    reconnecting = reconnecting,
    timecode = timecode,
    durationMs = duration,
    bytes = bytes,
    congestion = congestion,
    skippedFrames = skippedFrames,
    totalFrames = totalFrames,
)

internal fun GetRecordStatusResponse.toDomain() = RecordStatus(
    active = outputActive,
    paused = outputPaused,
    timecode = outputTimecode,
    durationMs = outputDuration,
    bytes = outputBytes,
)

internal fun GetStatsResponse.toDomain() = ObsStats(
    cpuUsage = cpuUsage,
    memoryUsageMb = memoryUsage,
    availableDiskSpaceMb = availableDiskSpace,
    activeFps = activeFps,
    averageFrameRenderTimeMs = averageFrameRenderTime,
    renderSkippedFrames = renderSkippedFrames,
    renderTotalFrames = renderTotalFrames,
    outputSkippedFrames = outputSkippedFrames,
    outputTotalFrames = outputTotalFrames,
)

internal fun GetProfileListResponse.toDomain() = ProfileListSnapshot(
    currentProfile = currentProfileName,
    profiles = profiles,
)

internal fun GetSceneCollectionListResponse.toDomain() = SceneCollectionListSnapshot(
    currentCollection = currentName,
    collections = collections,
)


internal fun GetSceneTransitionListResponse.toDomain() = TransitionListSnapshot(
    currentName = currentName.orEmpty(),
    transitions = transitions.map {
        TransitionInfo(
            name = it.name,
            kind = it.kind,
            fixed = it.fixed,
            durationMs = it.duration,
        )
    },
)

internal fun GetCurrentSceneTransitionResponse.toDomain() = CurrentTransition(
    name = name,
    kind = kind,
    durationMs = duration,
    configurable = configurable,
    fixed = fixed,
)
