package com.scenedeck.android.feature.history

import com.scenedeck.android.core.data.BookmarkOutputState
import com.scenedeck.android.core.data.BookmarkOutputTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun bookmarkDate(at: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(at))

internal fun outputTimeText(name: String, stamp: BookmarkOutputTime): String =
    when (stamp.state) {
        BookmarkOutputState.UNKNOWN -> "$name · unknown at capture"
        BookmarkOutputState.INACTIVE -> "$name · inactive at capture"
        BookmarkOutputState.ACTIVE,
        BookmarkOutputState.RECONNECTING,
        BookmarkOutputState.PAUSED -> {
            val time = stamp.durationMs ?: 0
            val duration =
                String.format(
                    Locale.ROOT,
                    "%02d:%02d:%02d.%03d",
                    time / 3_600_000,
                    time / 60_000 % 60,
                    time / 1_000 % 60,
                    time % 1_000,
                )
            val paused =
                when (stamp.state) {
                    BookmarkOutputState.PAUSED -> " · paused"
                    BookmarkOutputState.RECONNECTING -> " · reconnecting"
                    else -> ""
                }
            "$name · $duration$paused"
        }
    }
