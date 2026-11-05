package com.scenedeck.android.core.data

import com.scenedeck.android.core.database.ShortcutBindingEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_SHORTCUT_PAYLOAD_LENGTH = 8_192
private val shortcutJson = Json { encodeDefaults = true }
private val supportedShortcutKey = Regex("(?:[A-Z0-9]|F(?:[1-9]|1[0-2])|NUM[0-9])")

@Serializable private data class ShortcutPayload(val version: Int, val action: ShortcutAction)

fun ShortcutChord.isSupported(): Boolean = supportedShortcutKey.matches(key) && modifiers in 0..15

internal fun ShortcutDraft.checked(): ShortcutDraft {
    requireShortcut(id.isNotBlank() && id.length <= 128)
    requireShortcut(chord?.isSupported() == true)
    when (val target = action) {
        is ShortcutAction.Scene -> checkShortcutTarget(target.name)
        is ShortcutAction.Mute -> checkShortcutTarget(target.inputName)
        null -> requireShortcut(false)
        ShortcutAction.TakePreview,
        is ShortcutAction.Stream,
        is ShortcutAction.Recording -> Unit
    }
    return this
}

private fun checkShortcutTarget(name: String) {
    requireShortcut(name.isNotBlank() && name.length <= 1_024)
}

internal fun requireShortcut(
    value: Boolean,
    reason: ShortcutEditFailure = ShortcutEditFailure.INVALID,
) {
    if (!value) throw ShortcutEditException(reason)
}

internal fun ShortcutBinding.encodeShortcut(): ShortcutBindingEntity {
    draft().checked()
    requireShortcut(
        revision >= 0 && workspace.profileId > 0 && workspace.collectionName.isNotBlank()
    )
    val payload = shortcutJson.encodeToString(ShortcutPayload(version = 1, action = action))
    requireShortcut(payload.length <= MAX_SHORTCUT_PAYLOAD_LENGTH)
    return ShortcutBindingEntity(
        workspace.profileId,
        workspace.collectionName,
        id,
        revision,
        chord.key,
        chord.modifiers,
        enabled,
        payload,
    )
}

internal fun ShortcutBindingEntity.decodeShortcut(): ShortcutBinding {
    requireShortcut(payload.length <= MAX_SHORTCUT_PAYLOAD_LENGTH, ShortcutEditFailure.STORAGE)
    val decoded = shortcutJson.decodeFromString<ShortcutPayload>(payload)
    requireShortcut(decoded.version == 1, ShortcutEditFailure.STORAGE)
    return ShortcutBinding(
            WorkspaceKey(connectionProfileId, sceneCollectionName),
            bindingId,
            revision,
            ShortcutChord(key, modifiers),
            decoded.action,
            enabled,
        )
        .also { it.encodeShortcut() }
}

internal fun List<ShortcutBindingEntity>.decodeShortcuts(): List<ShortcutBinding> {
    requireShortcut(size <= MAX_SHORTCUT_BINDINGS, ShortcutEditFailure.STORAGE)
    return map { it.decodeShortcut() }
}
