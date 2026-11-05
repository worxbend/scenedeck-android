package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.NamedDeckEntity
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MAX_DECK_DOCUMENT_BYTES = 4 * 1024 * 1024

@Serializable data class DeckDocumentEntry(val name: String, val scenes: List<String>)

@Serializable
private data class DeckDocument(
    val format: String,
    val version: Int,
    val decks: List<DeckDocumentEntry>,
)

/** Portable local deck definitions only: no device IDs, connection details or credentials. */
object NamedDeckCodec {
    private const val FORMAT = "scenedeck-decks"
    private val json = Json { prettyPrint = true }

    fun encode(snapshot: NamedDeckSnapshot): String =
        json
            .encodeToString(
                DeckDocument(
                    FORMAT,
                    1,
                    snapshot.decks.map { DeckDocumentEntry(it.name, it.sceneNames) },
                )
            )
            .also { requireDocumentSize(it) }

    fun decode(payload: String): List<DeckDocumentEntry> {
        requireDocumentSize(payload)
        val document = runCatching {
            json.decodeFromString<DeckDocument>(payload)
        }
            .getOrElse { throw DeckEditException(DeckEditFailure.INVALID_FILE) }
        if (document.format != FORMAT || document.version != 1)
            throw DeckEditException(DeckEditFailure.INVALID_FILE)
        return checkedDocumentEntries(document.decks)
    }

    private fun requireDocumentSize(payload: String) {
        if (
            payload.length > MAX_DECK_DOCUMENT_BYTES ||
                payload.toByteArray().size > MAX_DECK_DOCUMENT_BYTES
        )
            throw DeckEditException(DeckEditFailure.INVALID_FILE)
    }
}

@ConsistentCopyVisibility
data class DeckImportPreview
internal constructor(
    val origin: WorkspaceSession,
    val entries: List<DeckDocumentEntry>,
    val previous: List<NamedDeck>,
) {
    val replacementNames: Set<String>
        get() {
            val previousNames = previous.map { it.name.deckNameKey() }.toSet()
            return entries.filter { it.name.deckNameKey() in previousNames }.map { it.name }.toSet()
        }

    val replacementCount: Int
        get() = replacementNames.size

    val additionCount: Int
        get() = entries.size - replacementCount
}

suspend fun NamedDeckRepository.previewImport(
    origin: WorkspaceSession,
    payload: String,
): DeckImportPreview {
    val entries = NamedDeckCodec.decode(payload)
    val previous = snapshot(origin).decks
    val preview = DeckImportPreview(origin, entries, previous)
    if (previous.size + preview.additionCount > MAX_NAMED_DECKS)
        throw DeckEditException(DeckEditFailure.TOO_MANY_DECKS)
    return preview
}

/**
 * Explicit preview confirmation replaces same-name definitions, retains all others and selection.
 */
suspend fun NamedDeckRepository.applyImport(preview: DeckImportPreview) {
    val entries = checkedDocumentEntries(preview.entries)
    mutate(preview.origin) { dao ->
        val key = preview.origin.key
        val current = dao.snapshot(key.profileId, key.collectionName).toDeckSnapshot().decks
        if (current != preview.previous) throw DeckEditException(DeckEditFailure.DECK_CHANGED)
        val existing = current.associateBy { it.name.deckNameKey() }
        if (current.size + entries.count { it.name.deckNameKey() !in existing } > MAX_NAMED_DECKS)
            throw DeckEditException(DeckEditFailure.TOO_MANY_DECKS)
        entries.forEach { entry ->
            val target = existing[entry.name.deckNameKey()]
            val id = target?.id ?: "deck:${UUID.randomUUID()}"
            if (target == null) {
                dao.insert(
                    NamedDeckEntity(
                        key.profileId,
                        key.collectionName,
                        id,
                        entry.name,
                        entry.name.deckNameKey(),
                    )
                )
            } else {
                dao.rename(
                    key.profileId,
                    key.collectionName,
                    id,
                    entry.name,
                    entry.name.deckNameKey(),
                )
                dao.clearMembers(key.profileId, key.collectionName, id)
            }
            dao.insertMembers(entry.scenes.toDeckMembers(key, id))
        }
    }
}

private fun checkedDocumentEntries(entries: List<DeckDocumentEntry>): List<DeckDocumentEntry> {
    if (entries.size > MAX_NAMED_DECKS) throw DeckEditException(DeckEditFailure.TOO_MANY_DECKS)
    val checked = entries.map {
        DeckDocumentEntry(checkedDeckName(it.name), checkedDeckMembers(it.scenes))
    }
    if (checked.map { it.name.deckNameKey() }.distinct().size != checked.size)
        throw DeckEditException(DeckEditFailure.DUPLICATE_NAME)
    return checked
}
