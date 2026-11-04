package com.scenedeck.android.core.obs.internal

import com.rejeq.ktobs.EventOpCode
import com.rejeq.ktobs.ObsSession
import com.rejeq.ktobs.event.config.CurrentProfileChangedEvent
import com.rejeq.ktobs.event.config.CurrentProfileChangedEventData
import com.rejeq.ktobs.event.config.CurrentSceneCollectionChangedEvent
import com.rejeq.ktobs.event.config.CurrentSceneCollectionChangedEventData
import com.rejeq.ktobs.event.config.CurrentSceneCollectionChangingEvent
import com.rejeq.ktobs.event.config.CurrentSceneCollectionChangingEventData
import com.rejeq.ktobs.event.config.ProfileListChangedEvent
import com.rejeq.ktobs.event.config.ProfileListChangedEventData
import com.rejeq.ktobs.event.config.SceneCollectionListChangedEvent
import com.rejeq.ktobs.event.config.SceneCollectionListChangedEventData
import com.rejeq.ktobs.event.inputs.InputAudioBalanceChangedEvent
import com.rejeq.ktobs.event.inputs.InputAudioBalanceChangedEventData
import com.rejeq.ktobs.event.inputs.InputAudioMonitorTypeChangedEvent
import com.rejeq.ktobs.event.inputs.InputAudioMonitorTypeChangedEventData
import com.rejeq.ktobs.event.inputs.InputAudioSyncOffsetChangedEvent
import com.rejeq.ktobs.event.inputs.InputAudioSyncOffsetChangedEventData
import com.rejeq.ktobs.event.inputs.InputCreatedEvent
import com.rejeq.ktobs.event.inputs.InputCreatedEventData
import com.rejeq.ktobs.event.inputs.InputMuteStateChangedEvent
import com.rejeq.ktobs.event.inputs.InputMuteStateChangedEventData
import com.rejeq.ktobs.event.inputs.InputNameChangedEvent
import com.rejeq.ktobs.event.inputs.InputNameChangedEventData
import com.rejeq.ktobs.event.inputs.InputRemovedEvent
import com.rejeq.ktobs.event.inputs.InputRemovedEventData
import com.rejeq.ktobs.event.inputs.InputVolumeChangedEvent
import com.rejeq.ktobs.event.inputs.InputVolumeChangedEventData
import com.rejeq.ktobs.event.inputs.InputVolumeMetersEvent
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackEndedEvent
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackEndedEventData
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackStartedEvent
import com.rejeq.ktobs.event.mediainputs.MediaInputPlaybackStartedEventData
import com.rejeq.ktobs.event.outputs.RecordStateChangedEvent
import com.rejeq.ktobs.event.outputs.RecordStateChangedEventData
import com.rejeq.ktobs.event.outputs.StreamStateChangedEvent
import com.rejeq.ktobs.event.outputs.StreamStateChangedEventData
import com.rejeq.ktobs.event.outputs.VirtualcamStateChangedEvent
import com.rejeq.ktobs.event.outputs.VirtualcamStateChangedEventData
import com.rejeq.ktobs.event.sceneitems.SceneItemEnableStateChangedEvent
import com.rejeq.ktobs.event.sceneitems.SceneItemEnableStateChangedEventData
import com.rejeq.ktobs.event.scenes.CurrentPreviewSceneChangedEvent
import com.rejeq.ktobs.event.scenes.CurrentPreviewSceneChangedEventData
import com.rejeq.ktobs.event.scenes.CurrentProgramSceneChangedEvent
import com.rejeq.ktobs.event.scenes.CurrentProgramSceneChangedEventData
import com.rejeq.ktobs.event.scenes.SceneCreatedEvent
import com.rejeq.ktobs.event.scenes.SceneCreatedEventData
import com.rejeq.ktobs.event.scenes.SceneListChangedEvent
import com.rejeq.ktobs.event.scenes.SceneListChangedEventData
import com.rejeq.ktobs.event.scenes.SceneNameChangedEvent
import com.rejeq.ktobs.event.scenes.SceneNameChangedEventData
import com.rejeq.ktobs.event.scenes.SceneRemovedEvent
import com.rejeq.ktobs.event.scenes.SceneRemovedEventData
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionChangedEvent
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionChangedEventData
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionDurationChangedEvent
import com.rejeq.ktobs.event.transitions.CurrentSceneTransitionDurationChangedEventData
import com.rejeq.ktobs.event.transitions.SceneTransitionEndedEvent
import com.rejeq.ktobs.event.transitions.SceneTransitionEndedEventData
import com.rejeq.ktobs.event.transitions.SceneTransitionStartedEvent
import com.rejeq.ktobs.event.transitions.SceneTransitionStartedEventData
import com.rejeq.ktobs.event.ui.StudioModeStateChangedEvent
import com.rejeq.ktobs.event.ui.StudioModeStateChangedEventData
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.VolumeMeterReading
import com.scenedeck.android.core.obs.internal.protocol.InputVolumeMetersPayload
import com.scenedeck.android.core.obs.internal.protocol.toDomain
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Maps raw obs-websocket events to domain [ObsEvent]s and meter batches, then fans them out onto
 * the client's shared flows. A malformed event must never kill the receiver loop.
 */
internal class ObsEventDispatcher(
    private val emitEvent: (Long, ObsEvent) -> Unit,
    private val emitMeters: (Long, List<VolumeMeterReading>) -> Unit,
) {

    fun dispatch(session: ObsSession, event: EventOpCode, sessionId: Long) {
        runCatching {
            when (event.eventType) {
                InputVolumeMetersEvent -> session.dispatchMeters(event, sessionId)
                CurrentProgramSceneChangedEvent,
                SceneListChangedEvent,
                SceneCreatedEvent,
                SceneRemovedEvent,
                SceneNameChangedEvent,
                SceneItemEnableStateChangedEvent -> session.dispatchSceneEvent(event, sessionId)
                InputCreatedEvent,
                InputRemovedEvent,
                InputNameChangedEvent,
                InputMuteStateChangedEvent,
                InputVolumeChangedEvent -> session.dispatchInputEvent(event, sessionId)
                StreamStateChangedEvent,
                RecordStateChangedEvent,
                VirtualcamStateChangedEvent -> session.dispatchOutputEvent(event, sessionId)
                MediaInputPlaybackStartedEvent,
                MediaInputPlaybackEndedEvent,
                InputAudioBalanceChangedEvent,
                InputAudioSyncOffsetChangedEvent,
                InputAudioMonitorTypeChangedEvent -> session.dispatchInputEvent(event, sessionId)
                CurrentProfileChangedEvent,
                ProfileListChangedEvent,
                CurrentSceneCollectionChangingEvent,
                CurrentSceneCollectionChangedEvent,
                SceneCollectionListChangedEvent,
                StudioModeStateChangedEvent -> session.dispatchConfigEvent(event, sessionId)
                CurrentPreviewSceneChangedEvent,
                SceneTransitionStartedEvent,
                SceneTransitionEndedEvent,
                CurrentSceneTransitionChangedEvent,
                CurrentSceneTransitionDurationChangedEvent ->
                    session.dispatchStudioEvent(event, sessionId)
            }
        }
    }

    private fun ObsSession.dispatchMeters(event: EventOpCode, sessionId: Long) {
        val data = event.eventData ?: return
        val payload = ws.json.decodeFromJsonElement<InputVolumeMetersPayload>(data)
        emitMeters(sessionId, payload.toDomain())
    }

    private fun ObsSession.dispatchSceneEvent(event: EventOpCode, sessionId: Long) {
        val domain: ObsEvent =
            when (event.eventType) {
                CurrentProgramSceneChangedEvent ->
                    ObsEvent.CurrentProgramSceneChanged(
                        event.get<CurrentProgramSceneChangedEventData>().sceneName
                    )
                SceneListChangedEvent ->
                    ObsEvent.SceneListChanged(
                        event.get<SceneListChangedEventData>().scenes.map {
                            SceneSummary(name = it.name, index = it.index)
                        }
                    )
                SceneCreatedEvent ->
                    event.get<SceneCreatedEventData>().let {
                        ObsEvent.SceneCreated(it.sceneName, it.isGroup)
                    }
                SceneRemovedEvent ->
                    ObsEvent.SceneRemoved(event.get<SceneRemovedEventData>().sceneName)
                SceneNameChangedEvent ->
                    event.get<SceneNameChangedEventData>().let {
                        ObsEvent.SceneNameChanged(it.oldSceneName, it.sceneName)
                    }
                SceneItemEnableStateChangedEvent ->
                    event.get<SceneItemEnableStateChangedEventData>().let {
                        ObsEvent.SceneItemEnableStateChanged(
                            it.sceneName,
                            it.sceneItemId.toInt(),
                            it.sceneItemEnabled,
                        )
                    }
                else -> return
            }
        emitEvent(sessionId, domain)
    }

    private fun ObsSession.dispatchInputEvent(event: EventOpCode, sessionId: Long) {
        val domain = mapInputLifecycleEvent(event) ?: mapInputAudioEvent(event) ?: return
        emitEvent(sessionId, domain)
    }

    private fun ObsSession.mapInputLifecycleEvent(event: EventOpCode): ObsEvent? =
        when (event.eventType) {
            InputCreatedEvent ->
                event.get<InputCreatedEventData>().let {
                    ObsEvent.InputCreated(it.inputName, it.inputKind)
                }
            InputRemovedEvent -> ObsEvent.InputRemoved(event.get<InputRemovedEventData>().inputName)
            InputNameChangedEvent ->
                event.get<InputNameChangedEventData>().let {
                    ObsEvent.InputNameChanged(it.oldInputName, it.inputName)
                }
            InputMuteStateChangedEvent ->
                event.get<InputMuteStateChangedEventData>().let {
                    ObsEvent.InputMuteStateChanged(it.inputName, it.inputMuted)
                }
            InputVolumeChangedEvent ->
                event.get<InputVolumeChangedEventData>().let {
                    ObsEvent.InputVolumeChanged(it.inputName, it.inputVolumeMul, it.inputVolumeDb)
                }
            else -> null
        }

    private fun ObsSession.mapInputAudioEvent(event: EventOpCode): ObsEvent? =
        when (event.eventType) {
            MediaInputPlaybackStartedEvent ->
                ObsEvent.MediaInputPlaybackStarted(
                    event.get<MediaInputPlaybackStartedEventData>().inputName
                )
            MediaInputPlaybackEndedEvent ->
                ObsEvent.MediaInputPlaybackEnded(
                    event.get<MediaInputPlaybackEndedEventData>().inputName
                )
            InputAudioBalanceChangedEvent ->
                event.get<InputAudioBalanceChangedEventData>().let {
                    ObsEvent.InputAudioBalanceChanged(it.inputName, it.inputAudioBalance)
                }
            InputAudioSyncOffsetChangedEvent ->
                event.get<InputAudioSyncOffsetChangedEventData>().let {
                    ObsEvent.InputAudioSyncOffsetChanged(it.inputName, it.inputAudioSyncOffset)
                }
            InputAudioMonitorTypeChangedEvent ->
                event.get<InputAudioMonitorTypeChangedEventData>().let {
                    ObsEvent.InputAudioMonitorTypeChanged(it.inputName, it.monitorType.toDomain())
                }
            else -> null
        }

    private fun ObsSession.dispatchOutputEvent(event: EventOpCode, sessionId: Long) {
        val domain: ObsEvent =
            when (event.eventType) {
                StreamStateChangedEvent ->
                    event.get<StreamStateChangedEventData>().let {
                        ObsEvent.StreamStateChanged(it.outputActive, it.outputState)
                    }
                VirtualcamStateChangedEvent ->
                    event.get<VirtualcamStateChangedEventData>().let {
                        ObsEvent.VirtualcamStateChanged(it.outputActive, it.outputState)
                    }
                RecordStateChangedEvent ->
                    event.get<RecordStateChangedEventData>().let {
                        ObsEvent.RecordStateChanged(it.outputActive, it.outputState, it.outputPath)
                    }
                else -> return
            }
        emitEvent(sessionId, domain)
    }

    private fun ObsSession.dispatchStudioEvent(event: EventOpCode, sessionId: Long) {
        val domain: ObsEvent =
            when (event.eventType) {
                CurrentPreviewSceneChangedEvent ->
                    ObsEvent.CurrentPreviewSceneChanged(
                        event.get<CurrentPreviewSceneChangedEventData>().sceneName
                    )
                SceneTransitionStartedEvent ->
                    ObsEvent.SceneTransitionStarted(
                        event.get<SceneTransitionStartedEventData>().transitionName
                    )
                SceneTransitionEndedEvent ->
                    ObsEvent.SceneTransitionEnded(
                        event.get<SceneTransitionEndedEventData>().transitionName
                    )
                CurrentSceneTransitionChangedEvent ->
                    ObsEvent.CurrentSceneTransitionChanged(
                        event.get<CurrentSceneTransitionChangedEventData>().transitionName
                    )
                CurrentSceneTransitionDurationChangedEvent ->
                    ObsEvent.CurrentSceneTransitionDurationChanged(
                        event
                            .get<CurrentSceneTransitionDurationChangedEventData>()
                            .transitionDuration
                    )
                else -> return
            }
        emitEvent(sessionId, domain)
    }

    private fun ObsSession.dispatchConfigEvent(event: EventOpCode, sessionId: Long) {
        val domain: ObsEvent =
            when (event.eventType) {
                CurrentProfileChangedEvent ->
                    ObsEvent.CurrentProfileChanged(
                        event.get<CurrentProfileChangedEventData>().profileName
                    )
                ProfileListChangedEvent ->
                    ObsEvent.ProfileListChanged(event.get<ProfileListChangedEventData>().profiles)
                CurrentSceneCollectionChangingEvent ->
                    ObsEvent.CurrentSceneCollectionChanging(
                        event.get<CurrentSceneCollectionChangingEventData>().sceneCollectionName
                    )
                CurrentSceneCollectionChangedEvent ->
                    ObsEvent.CurrentSceneCollectionChanged(
                        event.get<CurrentSceneCollectionChangedEventData>().sceneCollectionName
                    )
                SceneCollectionListChangedEvent ->
                    ObsEvent.SceneCollectionListChanged(
                        event.get<SceneCollectionListChangedEventData>().sceneCollections
                    )
                StudioModeStateChangedEvent ->
                    ObsEvent.StudioModeStateChanged(
                        event.get<StudioModeStateChangedEventData>().studioModeEnabled
                    )
                else -> return
            }
        emitEvent(sessionId, domain)
    }
}
