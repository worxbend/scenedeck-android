package com.scenedeck.android.core.obs

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsSessionToken
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Binds a compound operation to the identity captured by its caller, before any suspension. */
suspend fun <T> ObsClient.withSession(
    token: ObsSessionToken,
    action: suspend ObsClient.() -> T,
): T =
    withContext(ObsRequestOrigin(token)) {
        requireSession(token)
        val result = action()
        requireSession(token)
        result
    }

private fun ObsClient.requireSession(token: ObsSessionToken) {
    val ready = connectionState.value as? ConnectionState.Ready ?: throw ObsNotConnectedException()
    if (ready.collectionChanging || ready.token != token) throw ObsWorkspaceChangedException()
}

/** The production request lane checks this again at admission, closing the caller-check race. */
internal class ObsRequestOrigin(val token: ObsSessionToken) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ObsRequestOrigin>
}
