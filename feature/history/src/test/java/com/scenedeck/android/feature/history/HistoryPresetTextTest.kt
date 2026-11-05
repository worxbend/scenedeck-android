package com.scenedeck.android.feature.history

import com.scenedeck.android.core.data.HistoryDetailState
import com.scenedeck.android.core.data.PresetRunOutcome
import com.scenedeck.android.core.data.PresetStopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPresetTextTest {
    @Test
    fun lateOutcomeStaysAtInvocationPointAndMakesPartialResultExplicit() {
        val detail = (historyPreviewState(true).selection.detail as HistoryDetailState.Ready).detail
        val timeline = detail.timeline()
        assertEquals(listOf(0L, 5_000L, 30_000L, 55_000L, 60_000L), timeline.map { it.elapsedMs })
        val preset = timeline[3]
        assertEquals("Preset · Interview start", preset.title)
        assertTrue(preset.lines.contains("1. Unmute: Guest mic · confirmed"))
        assertTrue(preset.lines.contains("2. Start recording · unknown"))
        assertTrue(preset.lines.contains("3. Program: Guest camera · not run"))
        assertTrue(preset.lines.any { it.contains("may have taken effect") })
    }

    @Test
    fun allSanitizedStopReasonsHaveReadableTextAndRecoveryDoesNotInventFinishTime() {
        val preset = historyPreviewPreset(10_000)
        PresetStopReason.entries.forEach { reason ->
            assertTrue(
                preset
                    .copy(run = preset.run.copy(reason = reason))
                    .timelineItem()
                    .lines
                    .isNotEmpty()
            )
        }
        val recovered =
            preset
                .copy(
                    run =
                        preset.run.copy(
                            outcome = PresetRunOutcome.INTERRUPTED,
                            reason = PresetStopReason.PROCESS_ENDED,
                            finishedAtMs = null,
                        )
                )
                .timelineItem()
        assertTrue(recovered.lines.contains("App interrupted · finish time unknown."))
        assertFalse(recovered.lines.any { it.startsWith("Finished") })
    }
}
