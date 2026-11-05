package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.MediaActionKind
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsEvent

internal class DemoMedia(private val clock: () -> Long, private val emit: (ObsEvent) -> Unit) {
    private var state = MediaStateKind.STOPPED
    private var position = 0L
    private var since = clock()

    fun status(name: String): MediaStatus {
        requireClip(name)
        val elapsed = if (state == MediaStateKind.PLAYING) (clock() - since).coerceAtLeast(0) else 0
        val cursor = (position + elapsed).coerceAtMost(DURATION_MS)
        if (state == MediaStateKind.PLAYING && cursor == DURATION_MS) {
            state = MediaStateKind.ENDED
            position = cursor
            emit(ObsEvent.MediaInputPlaybackEnded(name))
        }
        return MediaStatus(state, DURATION_MS, cursor)
    }

    fun seek(name: String, cursorMs: Long) {
        val current = status(name)
        demoRequire(
            current.state == MediaStateKind.PLAYING || current.state == MediaStateKind.PAUSED
        )
        position = cursorMs.coerceIn(0, DURATION_MS)
        since = clock()
    }

    fun action(name: String, action: MediaActionKind) {
        val current = status(name)
        when (action) {
            MediaActionKind.PLAY ->
                play(if (current.state == MediaStateKind.ENDED) 0 else current.cursorMs ?: 0)
            MediaActionKind.RESTART -> play(0)
            MediaActionKind.PAUSE -> {
                demoRequire(state == MediaStateKind.PLAYING)
                position = current.cursorMs ?: 0
                state = MediaStateKind.PAUSED
            }
            MediaActionKind.STOP -> {
                position = 0
                state = MediaStateKind.STOPPED
                emit(ObsEvent.MediaInputPlaybackEnded(name))
            }
            else -> demoRequire(false)
        }
    }

    private fun play(cursor: Long) {
        position = cursor
        since = clock()
        state = MediaStateKind.PLAYING
        emit(ObsEvent.MediaInputPlaybackStarted(DemoCatalog.CLIP))
    }

    private fun requireClip(name: String) {
        demoRequire(DemoCatalog.inputs.any { it.name == name }, missing = true)
        demoRequire(name == DemoCatalog.CLIP)
    }

    companion object {
        const val DURATION_MS = 90_000L
    }
}
