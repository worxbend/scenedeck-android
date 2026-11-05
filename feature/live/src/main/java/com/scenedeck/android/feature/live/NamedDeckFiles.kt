package com.scenedeck.android.feature.live

import android.content.ContentResolver
import android.net.Uri
import com.scenedeck.android.core.common.coroutineResult
import com.scenedeck.android.core.data.DeckEditException
import com.scenedeck.android.core.data.DeckEditFailure
import com.scenedeck.android.core.data.DeckImportPreview
import com.scenedeck.android.core.data.MAX_DECK_DOCUMENT_BYTES
import com.scenedeck.android.core.data.NamedDeckCodec
import com.scenedeck.android.core.data.NamedDeckRepository
import com.scenedeck.android.core.data.WorkspaceSession
import com.scenedeck.android.core.data.previewImport
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Captures the originating workspace before the external picker can suspend the UI. */
internal class NamedDeckFiles(
    private val repository: NamedDeckRepository,
    private val scope: CoroutineScope,
    private val onPreview: (DeckImportPreview) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private var pending: WorkspaceSession? = null
    private var working = false

    fun prepare(origin: WorkspaceSession): Boolean {
        if (pending != null || working || !repository.workspaces.isCurrent(origin)) return false
        pending = origin
        return true
    }

    fun importFrom(uri: Uri?, resolver: ContentResolver) =
        consume(uri) { origin, source ->
            val payload =
                withContext(Dispatchers.IO) {
                    val bytes =
                        requireNotNull(resolver.openInputStream(source)).use { it.readDeckBytes() }
                    if (bytes.size > MAX_DECK_DOCUMENT_BYTES)
                        throw DeckEditException(DeckEditFailure.INVALID_FILE)
                    bytes.toString(Charsets.UTF_8)
                }
            val preview =
                withContext(Dispatchers.Default) { repository.previewImport(origin, payload) }
            onPreview(preview)
        }

    fun exportTo(uri: Uri?, resolver: ContentResolver) =
        consume(uri) { origin, destination ->
            val snapshot = repository.snapshot(origin)
            val payload = withContext(Dispatchers.Default) { NamedDeckCodec.encode(snapshot) }
            withContext(Dispatchers.IO) {
                require(repository.workspaces.isCurrent(origin))
                requireNotNull(resolver.openOutputStream(destination, "wt")).use {
                    it.write(payload.toByteArray())
                }
            }
            onMessage("Deck backup saved.")
        }

    private fun consume(uri: Uri?, action: suspend (WorkspaceSession, Uri) -> Unit) {
        val origin = pending
        pending = null
        if (origin == null || uri == null || working) return
        working = true
        scope.launch {
            try {
                coroutineResult {
                    require(repository.workspaces.isCurrent(origin))
                    action(origin, uri)
                }
                    .onFailure {
                        onMessage(
                            "Couldn't transfer decks. Reopen the picker in the current workspace."
                        )
                    }
            } finally {
                working = false
            }
        }
    }
}

private fun InputStream.readDeckBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = read(buffer)
    while (count >= 0) {
        if (output.size() + count > MAX_DECK_DOCUMENT_BYTES)
            throw DeckEditException(DeckEditFailure.INVALID_FILE)
        output.write(buffer, 0, count)
        count = read(buffer)
    }
    return output.toByteArray()
}
