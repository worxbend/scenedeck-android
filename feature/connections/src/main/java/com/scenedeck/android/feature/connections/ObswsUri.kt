package com.scenedeck.android.feature.connections

import java.net.URI
import java.net.URLDecoder

/** Parses an `obsws://` URI; returns null for anything malformed or off-scheme. */
@Suppress(
    "ReturnCount"
) // Reject malformed external input immediately; avoid deeply nested parsing.
fun parseObswsUri(raw: String): ObswsTarget? {
    if (raw.length > MAX_PAIRING_URI_LENGTH) return null
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    if (!isPairingUri(uri)) return null
    val (host, port) = parseAuthority(uri) ?: return null
    val params = parseQuery(uri.rawQuery) ?: return null
    return ObswsTarget(
        host = host,
        port = port,
        password = params["password"]?.takeIf { it.isNotEmpty() },
        suggestedName = params["name"]?.takeIf { it.isNotEmpty() },
    )
}

private fun isPairingUri(uri: URI): Boolean =
    uri.scheme.equals("obsws", ignoreCase = true) &&
        (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
        uri.rawFragment == null

@Suppress("ReturnCount") // Each guard rejects a distinct malformed authority.
private fun parseAuthority(uri: URI): Pair<String, Int>? {
    // URI.getPort() silently maps garbage to -1; validate the raw authority too.
    val authority = uri.rawAuthority?.takeIf { it.isNotBlank() } ?: return null
    if ('@' in authority) return null
    val hostPort = authority.split(':')
    if (hostPort.size > 2) return null
    val host = hostPort[0]
    if (uri.host == null || host.length > MAX_HOST_LENGTH) return null
    val port =
        if (hostPort.size == 2) {
            hostPort[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else {
            DEFAULT_OBS_PORT
        }
    return host to port
}

@Suppress("ReturnCount") // Invalid or ambiguous credentials abort the entire query.
private fun parseQuery(rawQuery: String?): Map<String, String>? {
    val params = mutableMapOf<String, String>()
    for (part in rawQuery?.split('&').orEmpty()) {
        val idx = part.indexOf('=')
        if (idx <= 0) continue
        val key = part.substring(0, idx)
        val value =
            runCatching {
                URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8.name())
            }
                .getOrNull() ?: return null
        // Ambiguous duplicate credentials must never be silently overwritten.
        if (params.put(key, value) != null) return null
    }
    return params
}

private const val DEFAULT_OBS_PORT = 4455
private const val MAX_PAIRING_URI_LENGTH = 16_384
private const val MAX_HOST_LENGTH = 253
