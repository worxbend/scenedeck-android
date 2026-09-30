package com.scenedeck.android.feature.inventory

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounds user-selected document reads before decoding or allocating a large text payload. */
internal fun readRegistryDocument(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count =
            input.read(
                buffer,
                0,
                minOf(buffer.size, MAX_REGISTRY_DOCUMENT_BYTES + 1 - output.size()),
            )
        if (count < 0) break
        output.write(buffer, 0, count)
        require(output.size() <= MAX_REGISTRY_DOCUMENT_BYTES) { "Registry document exceeds 1 MiB" }
    }
    return output.toByteArray().decodeToString()
}

internal const val MAX_REGISTRY_DOCUMENT_BYTES = 1024 * 1024
