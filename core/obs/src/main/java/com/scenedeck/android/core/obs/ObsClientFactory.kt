package com.scenedeck.android.core.obs

import kotlinx.coroutines.CoroutineScope

/** Separate connection tests use the process policy before resolving any credentials. */
fun interface ObsClientFactory {
    fun create(scope: CoroutineScope): ObsClient
}

class ObsConnectionTestingUnavailableException :
    IllegalStateException("Connection tests are unavailable in demo")
