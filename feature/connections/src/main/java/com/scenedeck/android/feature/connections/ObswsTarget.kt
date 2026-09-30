package com.scenedeck.android.feature.connections

/**
 * Parsed `obsws://` pairing target.
 *
 * QR pairing URI scheme (SceneDeck, M2):
 * - `obsws://<host>[:<port>]` — port defaults to 4455.
 * - Optional query params: `password=<url-encoded>` (offered by some tools; used for the test
 *   connection but only stored after explicit user confirmation), `name=<url-encoded>` (suggested
 *   profile name).
 *
 * Example: `obsws://192.168.1.20:4455?password=s3cret&name=Studio%20rig`
 */
data class ObswsTarget(
    val host: String,
    val port: Int,
    val password: String? = null,
    val suggestedName: String? = null,
) {
    override fun toString(): String =
        "ObswsTarget(host=$host, port=$port, password=<redacted>, suggestedName=$suggestedName)"
}
