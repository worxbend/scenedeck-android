package com.scenedeck.android.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** The profile requested by the session owner, independent of the last-used preference. */
data class ConnectionTarget(
    val profileId: Long? = null,
    val generation: Long = 0,
    val confirmed: Boolean = false,
)

/** Only [ObsSessionHolder] changes this identity; consumers can observe but cannot select hosts. */
@Singleton
class ActiveConnection @Inject constructor() {
    private val current = MutableStateFlow(ConnectionTarget())
    val target = current.asStateFlow()

    /** Invalidates the previous target synchronously, before credential lookup can suspend. */
    internal fun begin(profileId: Long?): ConnectionTarget = current.updateAndGet {
        ConnectionTarget(profileId, it.generation + 1)
    }

    internal fun confirm(attempt: ConnectionTarget) {
        if (attempt.profileId != null)
            current.compareAndSet(attempt, attempt.copy(confirmed = true))
    }

    internal fun end(attempt: ConnectionTarget) {
        current.update {
            if (it.generation == attempt.generation) ConnectionTarget(generation = it.generation)
            else it
        }
    }
}
