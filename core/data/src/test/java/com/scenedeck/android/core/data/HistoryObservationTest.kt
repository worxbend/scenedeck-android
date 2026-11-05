package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.model.ObsSessionToken
import com.scenedeck.android.core.model.RecordingPhase
import com.scenedeck.android.core.model.StreamStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryObservationTest {
    private val inputs = HistoryInputs(TEST_WORKSPACE, connected = true)

    @Test
    fun initialUnknownCameraDoesNotPretendInactiveAndCounterTicksDoNotCreateEvents() {
        val observation = historyObservation()
        val initial = observation.snapshot(inputs, 1_000).filterIsInstance<HistoryPayload.Output>()
        assertEquals(3, initial.size)
        assertTrue(initial.all { it.state == HistoryOutputState.UNKNOWN && it.initial })
        assertTrue(observation.snapshot(inputs, 2_000).isEmpty())
        val telemetry =
            Telemetry(
                session = TEST_WORKSPACE.token,
                virtualCamActive = false,
                receivedAt = TelemetryReadTimes(virtualCam = 2_000),
                virtualCamReadStartedAtMs = 1_900,
            )
        val camera =
            observation.snapshot(inputs.copy(telemetry = telemetry), 2_000).single()
                as HistoryPayload.Output
        assertEquals(HistoryOutput.VIRTUAL_CAMERA, camera.output)
        assertEquals(HistoryOutputState.INACTIVE, camera.state)
        assertTrue(camera.initial)
        assertTrue(observation.snapshot(inputs.copy(telemetry = telemetry), 2_500).isEmpty())
    }

    @Test
    fun rapidProgramEventsArePreservedAndLateInitialSnapshotCannotUndoThem() {
        val observation = historyObservation()
        observation.snapshot(inputs.copy(program = HistoryProgram(TEST_WORKSPACE, "Camera")), 1_000)
        val changes =
            listOf("Talk", "Camera")
                .flatMap { name ->
                    observation.capture(
                        inputs.copy(program = HistoryProgram(TEST_WORKSPACE, "Old initial")),
                        ObsSessionEvent(
                            TEST_WORKSPACE.token,
                            ObsEvent.CurrentProgramSceneChanged(name),
                        ),
                        1_100,
                    )
                }
                .filterIsInstance<HistoryPayload.Program>()
        assertEquals(listOf("Talk", "Camera"), changes.map { it.sceneName })
        assertTrue(changes.none { it.initial })
        assertTrue(
            observation
                .snapshot(inputs.copy(program = HistoryProgram(TEST_WORKSPACE, "Old")), 1_200)
                .isEmpty()
        )
    }

    @Test
    fun eventBeforeInitialSnapshotWinsAndRenamesFollowTheObservedProgram() {
        val observation = historyObservation()
        val seeded =
            observation.capture(
                inputs.copy(program = HistoryProgram(TEST_WORKSPACE, "Old")),
                ObsSessionEvent(TEST_WORKSPACE.token, ObsEvent.CurrentProgramSceneChanged("New")),
                1_000,
            )
        assertEquals(
            listOf("New"),
            seeded.filterIsInstance<HistoryPayload.Program>().map { it.sceneName },
        )
        assertEquals(
            listOf(HistoryPayload.Program("Renamed", false)),
            observation.event(ObsEvent.SceneNameChanged("New", "Renamed"), 1_100),
        )
        assertTrue(
            observation.event(ObsEvent.SceneNameChanged("Unrelated", "Other"), 1_200).isEmpty()
        )
    }

    @Test
    fun preEventReadCannotRollBackOutputAndEvidenceExpiryIsUnknown() {
        val observation = historyObservation()
        observation.snapshot(inputs, 1_000)
        val event = ObsEvent.StreamStateChanged(true, "", reconnecting = true)
        assertEquals(
            HistoryOutputState.RECONNECTING,
            (observation.event(event, 2_000).single() as HistoryPayload.Output).state,
        )
        val telemetry =
            Telemetry(
                session = TEST_WORKSPACE.token,
                stream = StreamStatus(false, false, "", 0, 0, 0.0, 0, 0),
                receivedAt = TelemetryReadTimes(stream = 2_500),
                streamReadStartedAtMs = 1_999,
            )
        assertTrue(observation.snapshot(inputs.copy(telemetry = telemetry), 2_500).isEmpty())
        val expired =
            observation.snapshot(inputs.copy(telemetry = telemetry), 5_001).single()
                as HistoryPayload.Output
        assertEquals(HistoryOutputState.UNKNOWN, expired.state)
        val fresh =
            telemetry.copy(
                receivedAt = TelemetryReadTimes(stream = 5_002),
                streamReadStartedAtMs = 5_001,
            )
        assertEquals(
            HistoryOutputState.INACTIVE,
            (observation.snapshot(inputs.copy(telemetry = fresh), 5_002).single()
                    as HistoryPayload.Output)
                .state,
        )
    }

    @Test
    fun pausedRecordingUsesTypedPhaseAndDoesNotPersistRawStateOrPath() {
        val observation = historyObservation()
        val event =
            ObsEvent.RecordStateChanged(
                true,
                "private server string",
                "/private/file.mkv",
                RecordingPhase.PAUSED,
            )
        assertEquals(
            HistoryPayload.Output(HistoryOutput.RECORDING, HistoryOutputState.PAUSED, false),
            observation.event(event, 1_000).single(),
        )
    }

    @Test
    fun warningChangesRequireTheMatchingOriginAndDisappearanceDoesNotClaimRecovery() {
        val observation = historyObservation()
        observation.snapshot(inputs, 1_000)
        val origin =
            LiveAlertOrigin(
                TEST_WORKSPACE.key.profileId,
                TEST_WORKSPACE.targetGeneration,
                TEST_WORKSPACE.token,
            )
        val alert = LiveAlert(LiveAlertKind.DISK_SPACE, 4)
        val state = LiveAlertsState(alerts = listOf(alert), origin = origin)
        assertEquals(
            HistoryWarningPhase.SHOWN,
            (observation.snapshot(inputs.copy(alerts = state), 1_100).single()
                    as HistoryPayload.Warning)
                .phase,
        )
        assertTrue(
            observation
                .snapshot(
                    inputs.copy(
                        alerts = state.copy(origin = origin.copy(token = ObsSessionToken(9)))
                    ),
                    1_200,
                )
                .isEmpty()
        )
        val uncertain = state.copy(alerts = listOf(alert.copy(uncertain = true)))
        assertEquals(
            HistoryWarningPhase.UNCERTAIN,
            (observation.snapshot(inputs.copy(alerts = uncertain), 1_300).single()
                    as HistoryPayload.Warning)
                .phase,
        )
        assertEquals(
            HistoryWarningPhase.CLEARED_OR_DISMISSED,
            (observation
                    .snapshot(inputs.copy(alerts = state.copy(alerts = emptyList())), 1_400)
                    .single() as HistoryPayload.Warning)
                .phase,
        )
    }
}
