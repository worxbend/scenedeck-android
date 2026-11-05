package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.ObsInputSummary
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.obs.ObsRequestFailedException

internal object DemoCatalog {
    const val PROFILE = "Demo studio"
    const val COLLECTION = "DEMO · Studio"
    const val CAMERA = "Demo · Camera"
    const val INTERVIEW = "Demo · Interview"
    const val MIC = "Demo microphone"
    const val MUSIC = "Demo music"
    const val CLIP = "Demo clip"
    const val VIDEO = "Demo camera source"
    const val TITLE = "Demo title"

    val scenes =
        listOf("Demo · Welcome", CAMERA, INTERVIEW, "Demo · Break", "Demo · Closing").mapIndexed {
            index,
            name ->
            SceneSummary(name, 4 - index)
        }
    val inputs =
        listOf(
            ObsInputSummary(MIC, "wasapi_input_capture"),
            ObsInputSummary(MUSIC, "wasapi_output_capture"),
            ObsInputSummary(CLIP, "ffmpeg_source"),
            ObsInputSummary(VIDEO, "color_source_v3"),
            ObsInputSummary(TITLE, "text_ft2_source_v2"),
        )

    fun items(): MutableMap<String, List<SceneItemInfo>> =
        scenes
            .associate { scene ->
                val sources =
                    when (scene.name) {
                        CAMERA -> listOf(VIDEO, TITLE)
                        INTERVIEW -> listOf(CAMERA, TITLE)
                        "Demo · Break" -> listOf(CLIP, TITLE)
                        else -> listOf(TITLE)
                    }
                scene.name to
                    sources.mapIndexed { index, source ->
                        SceneItemInfo(
                            index + 1,
                            index,
                            source,
                            true,
                            false,
                            inputs.find { it.name == source }?.kind,
                        )
                    }
            }
            .toMutableMap()

    fun requireScene(name: String) = demoRequire(scenes.any { it.name == name }, missing = true)
}

internal fun demoRequire(valid: Boolean, missing: Boolean = false) {
    if (!valid)
        throw ObsRequestFailedException(
            "DemoControl",
            if (missing) "ResourceNotFound" else "InvalidResourceState",
            null,
        )
}
