package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.MediaStatus
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.ObsSessionEvent
import com.scenedeck.android.core.obs.ObsClient
import kotlinx.coroutines.flow.MutableStateFlow

/** Serializes context, read-order and expiry updates on the same lock used by seeks. */
internal class MediaPlaybackStore(
    private val client: ObsClient,
    private val workspaces: WorkspaceContext,
    private val clock: MonotonicClock,
    private val invalidate: (Set<MediaTarget>) -> Unit,
) {
    val lock = Any()
    private var sequence = 0L
    private val latestReads = mutableMapOf<MediaTarget, Long>()
    private val excluded = mutableSetOf<String>()
    private var sourceNames = emptySet<String>()
    val current = MutableStateFlow(MixerMediaStatus())
    val targets = MutableStateFlow<List<MediaTarget>>(emptyList())

    fun isCurrent(origin: WorkspaceSession): Boolean {
        val ready = client.connectionState.value as? ConnectionState.Ready
        return workspaces.isCurrent(origin) &&
            ready?.token == origin.token &&
            !ready.collectionChanging
    }

    fun reading(target: MediaTarget): MediaPlayback? =
        synchronized(lock) {
            current.value.readings[target.inputName]?.takeIf {
                it.target == target && isCurrent(target.workspace)
            }
        }

    fun publishSeek(target: MediaTarget, pending: Boolean, result: MediaSeekResult?) =
        synchronized(lock) {
            reading(target)?.let { update(it.copy(seeking = pending, result = result)) }
        }

    fun sync(origin: WorkspaceSession?, names: Set<String>, supported: Boolean) =
        synchronized(lock) {
            if (current.value.workspace != origin) {
                excluded.clear()
                latestReads.clear()
                current.value = MixerMediaStatus(origin)
            }
            sourceNames = names
            excluded.retainAll(names)
            val readings =
                if (origin == null) emptyMap()
                else
                    (names - excluded).associateWith { name ->
                        current.value.readings[name]
                            ?: MediaPlayback(MediaTarget(origin, name, ++sequence))
                    }
            current.value = MixerMediaStatus(origin, readings, supported)
            updateTargets()
        }

    private fun updateTargets() {
        targets.value = current.value.readings.values.map { it.target }
        latestReads.keys.retainAll(targets.value.toSet())
        invalidate(targets.value.toSet())
    }

    fun onEvent(stamped: ObsSessionEvent) =
        synchronized(lock) {
            val origin = current.value.workspace ?: return@synchronized
            if (stamped.token != origin.token || !isCurrent(origin)) return@synchronized
            val change = mediaChange(stamped.event) ?: return@synchronized
            when (change.kind) {
                MediaChangeKind.CREATED -> {
                    excluded.remove(change.name)
                    if (change.name in sourceNames)
                        update(MediaPlayback(MediaTarget(origin, change.name, ++sequence)))
                }
                MediaChangeKind.REMOVED -> {
                    excluded.add(change.name)
                    current.value =
                        current.value.copy(readings = current.value.readings - change.name)
                }
                MediaChangeKind.PLAYBACK ->
                    current.value.readings[change.name]?.let { old ->
                        update(MediaPlayback(old.target.copy(revision = ++sequence)))
                    }
            }
            updateTargets()
        }

    fun beginRead(target: MediaTarget): Long? =
        synchronized(lock) {
            if (reading(target) == null) null else (++sequence).also { latestReads[target] = it }
        }

    fun accept(target: MediaTarget, ticket: Long, result: Result<MediaStatus>): MediaPlayback? =
        synchronized(lock) {
            val old = reading(target)
            if (old == null || latestReads[target] != ticket) null
            else {
                old.copy(
                        status = result.getOrNull(),
                        receivedAtMs = clock.nowMs().takeIf { result.isSuccess },
                    )
                    .also(::update)
            }
        }

    private fun update(reading: MediaPlayback) {
        current.value =
            current.value.copy(
                readings = current.value.readings + (reading.target.inputName to reading)
            )
    }

    fun clearSamples(expiredOnly: Boolean = false) =
        synchronized(lock) {
            if (!expiredOnly) latestReads.clear()
            current.value =
                current.value.copy(
                    readings =
                        current.value.readings.mapValues { (_, value) ->
                            if (!expiredOnly || !value.isFresh(clock.nowMs()))
                                value.copy(status = null, receivedAtMs = null)
                            else value
                        }
                )
        }
}

private enum class MediaChangeKind {
    CREATED,
    REMOVED,
    PLAYBACK,
}

private data class MediaChange(val name: String, val kind: MediaChangeKind)

private fun mediaChange(event: ObsEvent): MediaChange? =
    when (event) {
        is ObsEvent.InputCreated -> MediaChange(event.inputName, MediaChangeKind.CREATED)
        is ObsEvent.InputRemoved -> MediaChange(event.inputName, MediaChangeKind.REMOVED)
        is ObsEvent.InputNameChanged -> MediaChange(event.oldInputName, MediaChangeKind.REMOVED)
        is ObsEvent.MediaInputPlaybackStarted ->
            MediaChange(event.inputName, MediaChangeKind.PLAYBACK)
        is ObsEvent.MediaInputPlaybackEnded ->
            MediaChange(event.inputName, MediaChangeKind.PLAYBACK)
        else -> null
    }
