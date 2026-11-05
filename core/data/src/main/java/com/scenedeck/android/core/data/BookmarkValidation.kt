package com.scenedeck.android.core.data

internal fun BookmarkTimes.checked(): BookmarkTimes {
    if (
        version != 1 ||
            stream.state == BookmarkOutputState.PAUSED ||
            recording.state == BookmarkOutputState.RECONNECTING
    )
        throw BookmarkEditException()
    stream.checkFields()
    recording.checkFields()
    return this
}

private fun BookmarkOutputTime.checkFields() {
    val valid =
        when (state) {
            BookmarkOutputState.UNKNOWN -> durationMs == null && sampleAgeMs == null
            BookmarkOutputState.INACTIVE -> durationMs == null && validAge()
            else -> durationMs != null && durationMs >= 0 && validAge()
        }
    if (!valid) throw BookmarkEditException()
}

private fun BookmarkOutputTime.validAge(): Boolean = sampleAgeMs != null && sampleAgeMs in 0..3_000
