package com.scenedeck.android.feature.history

internal data class HistoryTimelineItem(
    val key: String,
    val elapsedMs: Long,
    val atMs: Long,
    val title: String,
    val lines: List<String>,
    val order: Long,
)
