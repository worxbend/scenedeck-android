package com.scenedeck.android.core.data

import androidx.compose.runtime.Immutable
import com.scenedeck.android.core.database.ConnectionProfileEntity

/**
 * A named OBS connection profile (FEATURE_SPEC §1). The password is NOT part of
 * this type — it lives in Keystore-backed encrypted storage ([SecretsStore]).
 */
@Immutable
data class ConnectionProfile(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)

internal fun ConnectionProfileEntity.toDomain() = ConnectionProfile(
    id = id,
    name = name,
    host = host,
    port = port,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
)
