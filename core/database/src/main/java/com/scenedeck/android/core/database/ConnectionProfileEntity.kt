package com.scenedeck.android.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named OBS connection target. Passwords are NEVER stored here — they live in Keystore-backed
 * encrypted storage (:core:data SecretsStore).
 */
@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)
