package com.scenedeck.android.core.data

/** The four Output Safety toggles (FEATURE_SPEC §4; default = confirm stops only). */
data class OutputSafety(
    val confirmStartStream: Boolean = false,
    val confirmStopStream: Boolean = true,
    val confirmStartRecord: Boolean = false,
    val confirmStopRecord: Boolean = true,
)

internal fun UserSettings.outputSafety() = OutputSafety(
    confirmStartStream = confirmStartStream,
    confirmStopStream = confirmStopStream,
    confirmStartRecord = confirmStartRecord,
    confirmStopRecord = confirmStopRecord,
)

/** What should happen when the user taps a transport button. */
enum class OutputAction {
    /** Start the output right away. */
    PERFORM,

    /** Ask for confirmation first (Output Safety). */
    REQUIRE_CONFIRMATION,
}

/** Pure Output Safety gating (unit-tested). */
object OutputSafetyGate {

    fun streamAction(currentlyActive: Boolean, safety: OutputSafety): OutputAction =
        gate(currentlyActive, safety.confirmStartStream, safety.confirmStopStream)

    fun recordAction(currentlyActive: Boolean, safety: OutputSafety): OutputAction =
        gate(currentlyActive, safety.confirmStartRecord, safety.confirmStopRecord)

    private fun gate(active: Boolean, confirmStart: Boolean, confirmStop: Boolean): OutputAction =
        if (if (active) confirmStop else confirmStart) {
            OutputAction.REQUIRE_CONFIRMATION
        } else {
            OutputAction.PERFORM
        }
}
