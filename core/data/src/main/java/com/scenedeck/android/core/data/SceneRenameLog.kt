package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.SceneSummary

/**
 * Recent OBS scene renames, so a scene-list or preview read that was answered before a rename (but
 * applied after it) can be brought up to date instead of resurrecting the old name.
 *
 * Callers take a [mark] before the read and [follow] names read afterwards. Only the last
 * [capacity] renames are kept; a read that outlives more renames replays only the retained ones.
 * Replaying is a fallback: callers re-read first when renames landed during a read, because a read
 * answered after the renames would otherwise be renamed twice (see [followScenes]).
 */
internal class SceneRenameLog(private val capacity: Int = DEFAULT_CAPACITY) {
    private val renames = ArrayDeque<Pair<String, String>>()
    private var total = 0L

    @Synchronized fun mark(): Long = total

    @Synchronized
    fun record(oldName: String, newName: String) {
        renames.addLast(oldName to newName)
        total++
        if (renames.size > capacity) renames.removeFirst()
    }

    @Synchronized fun clear() = renames.clear()

    /** Applies, in order, every rename recorded after [since] to [name]. */
    @Synchronized
    fun follow(name: String?, since: Long): String? {
        if (name == null) return null
        val skip = (renames.size - (total - since)).coerceIn(0L, renames.size.toLong()).toInt()
        var current: String = name
        for (index in skip until renames.size) {
            val (old, new) = renames[index]
            if (current == old) current = new
        }
        return current
    }

    /**
     * [follow]s every scene name. If that would give two scenes one name, the read evidently
     * already reflected the renames (e.g. a new scene reused an old name), so it is kept as read.
     */
    @Synchronized
    fun followScenes(scenes: List<SceneSummary>, since: Long): List<SceneSummary> {
        val followed = scenes.map { it.copy(name = follow(it.name, since) ?: it.name) }
        return if (followed.distinctBy { it.name }.size == followed.size) followed else scenes
    }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
