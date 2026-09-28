package com.scenedeck.android.core.model

import androidx.compose.runtime.Immutable

/** Version/platform info from `GetVersion`, carried by [ConnectionState.Ready]. */
@Immutable
data class ObsVersionInfo(
    val obsVersion: String,
    val obsWebSocketVersion: String,
    val rpcVersion: Int,
    val platform: String? = null,
)
