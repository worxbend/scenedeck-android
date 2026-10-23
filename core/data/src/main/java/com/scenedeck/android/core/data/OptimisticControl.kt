package com.scenedeck.android.core.data

/**
 * Pending edits are separate from confirmed OBS state. Access under the repository control lock.
 */
internal class OptimisticControl<T : Any>(initial: T, initialRevision: Long) {
    private var confirmed = initial
    private var confirmedRevision = initialRevision
    private val pending = mutableMapOf<Long, T>()

    fun begin(revision: Long, value: T): T {
        pending[revision] = value
        return current()
    }

    fun observe(value: T, revision: Long): T {
        if (revision >= confirmedRevision) {
            confirmed = value
            confirmedRevision = revision
            pending.keys.removeAll { it <= revision }
        }
        return current()
    }

    fun complete(revision: Long, succeeded: Boolean): T {
        val value = pending.remove(revision)
        if (value != null && succeeded && revision > confirmedRevision) {
            confirmed = value
            confirmedRevision = revision
        }
        return current()
    }

    private fun current(): T =
        pending.entries.maxByOrNull { it.key }?.takeIf { it.key > confirmedRevision }?.value
            ?: confirmed
}
