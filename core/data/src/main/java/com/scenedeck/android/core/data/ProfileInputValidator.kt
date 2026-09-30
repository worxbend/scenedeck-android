package com.scenedeck.android.core.data

/** Pure input validation for the profile form (unit-tested). */
object ProfileInputValidator {

    fun validateName(name: String): String? =
        when {
            name.isBlank() -> "Name is required"
            else -> null
        }

    /** Hostname or IPv4 literal; no whitespace, no scheme, no port suffix. */
    fun validateHost(host: String): String? =
        when {
            host.isBlank() -> "Host is required"
            host.any { it.isWhitespace() } -> "Host must not contain spaces"
            host.contains("://") -> "Enter the host only, without a scheme"
            !host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' } ->
                "Host contains invalid characters"
            else -> null
        }

    fun validatePort(port: String): String? =
        when {
            port.isBlank() -> "Port is required"
            port.toIntOrNull() == null -> "Port must be a number"
            port.toInt() !in 1..65535 -> "Port must be between 1 and 65535"
            else -> null
        }

    fun isValid(name: String, host: String, port: String): Boolean =
        validateName(name) == null && validateHost(host) == null && validatePort(port) == null
}
