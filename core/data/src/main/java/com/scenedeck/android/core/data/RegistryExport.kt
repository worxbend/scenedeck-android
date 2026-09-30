package com.scenedeck.android.core.data

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

/** YAML wire format for registry backup/sharing (FEATURE_SPEC §6). */
@Serializable
data class RegistryExport(
    val format: String = FORMAT_ID,
    val version: Int = 1,
    val entries: List<RegistryExportEntry>,
) {
    companion object {
        const val FORMAT_ID = "scenedeck-registry"
    }
}

@Serializable
data class RegistryExportEntry(
    val sceneName: String,
    val role: String,
    val accentColorArgb: Long? = null,
    val iconName: String? = null,
    val sortOrder: Int = 0,
)

internal fun SceneRegistryEntry.toExport() =
    RegistryExportEntry(
        sceneName = sceneName,
        role = role.name,
        accentColorArgb = accentColorArgb,
        iconName = iconName,
        sortOrder = sortOrder,
    )

internal fun RegistryExportEntry.toDomain() =
    SceneRegistryEntry(
        sceneName = sceneName,
        role = runCatching { SceneRole.valueOf(role) }.getOrDefault(SceneRole.SECONDARY),
        accentColorArgb = accentColorArgb,
        iconName = iconName,
        sortOrder = sortOrder,
    )

/** YAML (kaml) codec for the registry export. JSON fallback is one flag away. */
object RegistryExportCodec {

    private val yaml = Yaml.default

    fun encode(entries: List<SceneRegistryEntry>): String =
        yaml.encodeToString(
            RegistryExport.serializer(),
            RegistryExport(entries = entries.map { it.toExport() }),
        )

    /** Throws [RegistryImportException] on malformed input or wrong format marker. */
    fun decode(payload: String): List<SceneRegistryEntry> {
        val parsed = runCatching {
            yaml.decodeFromString(RegistryExport.serializer(), payload)
        }
            .getOrElse { throw RegistryImportException("Not a valid registry file: ${it.message}") }
        if (parsed.format != RegistryExport.FORMAT_ID) {
            throw RegistryImportException("Not a SceneDeck registry file (format=${parsed.format})")
        }
        return parsed.entries.map { it.toDomain() }
    }
}

class RegistryImportException(message: String) : Exception(message)
