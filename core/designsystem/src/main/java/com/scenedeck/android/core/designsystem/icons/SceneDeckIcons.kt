package com.scenedeck.android.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * App-level UI icons for the shell (navigation, destinations). Same Lucide style
 * as [SceneIcon]; destination glyphs reuse catalogue entries.
 */
object SceneDeckIcons {
    val Scenes: ImageVector get() = SceneIcon.SCENES.imageVector
    val Mixer: ImageVector get() = SceneIcon.MIXER.imageVector
    val Stats: ImageVector get() = SceneIcon.STATS.imageVector
    val Inventory: ImageVector get() = SceneIcon.INVENTORY.imageVector
    val Graph: ImageVector get() = SceneIcon.GRAPH.imageVector
    val Doctor: ImageVector get() = SceneIcon.DOCTOR.imageVector
    val Settings: ImageVector get() = SceneIcon.SETTINGS.imageVector
    val Connections: ImageVector = appIcon(
        "Connections",
        "M17,19a1,1 0 0 1 -1,-1v-2a2,2 0 0 1 2,-2h2a2,2 0 0 1 2,2v2a1,1 0 0 1 -1,1z",
        "M17,21v-2",
        "M19,14V6.5a1,1 0 0 0 -7,0v11a1,1 0 0 1 -7,0V10",
        "M21,21v-2",
        "M3,5V3",
        "M4,10a2,2 0 0 1 -2,-2V6a1,1 0 0 1 1,-1h4a1,1 0 0 1 1,1v2a2,2 0 0 1 -2,2z",
        "M7,5V3",
    )
    val Help: ImageVector = appIcon(
        "Help",
        "M2,12 A10,10 0 1 0 22,12 A10,10 0 1 0 2,12 z",
        "M9.09,9a3,3 0 0 1 5.83,1c0,2 -3,3 -3,3",
        "M12,17h0.01",
    )
    val More: ImageVector = appIcon(
        "More",
        "M4,12 A1,1 0 1 0 6,12 A1,1 0 1 0 4,12 z",
        "M11,12 A1,1 0 1 0 13,12 A1,1 0 1 0 11,12 z",
        "M18,12 A1,1 0 1 0 20,12 A1,1 0 1 0 18,12 z",
    )
}

private fun appIcon(name: String, vararg elements: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        elements.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
