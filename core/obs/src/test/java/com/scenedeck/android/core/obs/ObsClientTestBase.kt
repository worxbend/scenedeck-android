package com.scenedeck.android.core.obs

import com.scenedeck.android.core.obs.internal.KtobsObsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before

internal const val TEST_BACKOFF_MS = 10L

internal abstract class ObsClientTestBase {
    protected lateinit var scope: CoroutineScope

    @Before
    fun baseSetup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun baseTeardown() {
        scope.cancel()
    }

    protected fun newClient(backoffMillis: (Int) -> Long = { TEST_BACKOFF_MS }): KtobsObsClient =
        KtobsObsClient(scope = scope, backoffMillis = backoffMillis)

    protected suspend fun connectedClient(
        server: FakeObsServer,
        password: String? = null,
    ): KtobsObsClient {
        server.enqueueSession()
        return newClient().apply { connect("127.0.0.1", server.port, password = password) }
    }
}
