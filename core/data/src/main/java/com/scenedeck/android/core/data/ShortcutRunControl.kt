package com.scenedeck.android.core.data

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/** One invocation owns consent. Repeated callbacks and callbacks for old IDs are inert. */
internal class ShortcutRunControl(val id: String, private val job: Job) {
    private var open = true
    private var waiting = false
    private var waitingAtMs = 0L
    private val consent = CompletableDeferred<Unit>()
    val issue = AtomicReference<ShortcutIssue?>(null)
    @Volatile
    var dispatched = false
        private set

    @Synchronized
    fun waiting(nowMs: Long) {
        if (open && issue.get() == null && job.isActive) {
            waitingAtMs = nowMs
            waiting = true
        }
    }

    @Synchronized
    fun confirm(nowMs: Long): Boolean {
        if (!open || !waiting) return false
        val accepted = nowMs >= waitingAtMs && nowMs - waitingAtMs <= 60_000
        waiting = false
        if (accepted) {
            consent.complete(Unit)
        } else stop(ShortcutIssue.EXPIRED)
        return accepted
    }

    suspend fun awaitConsent() = consent.await()

    @Synchronized
    fun dispatch() {
        requireShortcutState(open && issue.get() == null, issue.get() ?: ShortcutIssue.CANCELLED)
        dispatched = true
    }

    @Synchronized
    fun stop(reason: ShortcutIssue) {
        if (open) {
            waiting = false
            issue.compareAndSet(null, reason)
            job.cancel(CancellationException("Shortcut interrupted"))
        }
    }

    @Synchronized
    fun close() {
        open = false
        waiting = false
    }
}
