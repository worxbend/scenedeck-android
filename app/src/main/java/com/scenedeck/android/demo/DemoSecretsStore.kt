package com.scenedeck.android.demo

import com.scenedeck.android.core.data.SecretsStore

/** Demo has no credential backend, including no in-memory password cache. */
internal object DemoSecretsStore : SecretsStore {
    override suspend fun passwordFor(profileId: Long): String? = null

    override suspend fun setPassword(profileId: Long, password: String?) {
        check(password == null) { "Credentials are unavailable in demo" }
    }
}
