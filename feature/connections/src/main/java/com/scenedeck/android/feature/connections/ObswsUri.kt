package com.scenedeck.android.feature.connections

import java.net.URI
import java.net.URLDecoder

/** Parses an `obsws://` URI; returns null for anything malformed or off-scheme. */
@Suppress("ReturnCount") // early exits are the clearest form for a parser
fun parseObswsUri(raw: String): ObswsTarget? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("obsws", ignoreCase = true)) return null

    // Parse the authority ourselves: URI.getPort() silently maps garbage to -1.
    val authority = uri.rawAuthority?.takeIf { it.isNotBlank() } ?: return null
    if ('@' in authority) return null // no userinfo in obsws:// URIs
    val hostPort = authority.split(':')
    if (hostPort.size > 2) return null
    val host = hostPort[0].takeIf { it.isNotBlank() } ?: return null
    val port = if (hostPort.size == 2) {
        hostPort[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    } else {
        DEFAULT_OBS_PORT
    }

    val params = uri.rawQuery
        ?.split('&')
        ?.mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = part.substring(0, idx)
            val value = runCatching {
                URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            key to value
        }
        ?.toMap()
        .orEmpty()

    return ObswsTarget(
        host = host,
        port = port,
        password = params["password"]?.takeIf { it.isNotEmpty() },
        suggestedName = params["name"]?.takeIf { it.isNotEmpty() },
    )
}

private const val DEFAULT_OBS_PORT = 4455
