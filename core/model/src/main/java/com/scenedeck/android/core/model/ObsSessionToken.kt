package com.scenedeck.android.core.model

/** Identity of one connection and collection generation within an ObsClient. Never persisted. */
data class ObsSessionToken(val sessionId: Long = 0, val collectionRevision: Long = 0)

/** Stamped at receipt, before buffering, so delayed consumers can reject obsolete events. */
data class ObsSessionEvent(val token: ObsSessionToken, val event: ObsEvent)

/** Meter batches use the same origin identity as ordinary events. */
data class ObsSessionMeters(val token: ObsSessionToken, val readings: List<VolumeMeterReading>)
