package com.scenedeck.android.core.data

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Unlike a History capture owner, this identity does not change on Retry capture. */
@Singleton
class LocalProcessIdentity @Inject constructor() {
    val id: String = UUID.randomUUID().toString()
}
