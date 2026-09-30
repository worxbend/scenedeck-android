@file:Suppress(
    "MaxLineLength"
) // SVG path data strings are atomic data; wrapping hurts readability.

package com.scenedeck.android.core.designsystem.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Icon catalogue (docs/DESIGN_SYSTEM.md §6). No Lucide artifact is published on
// Maven Central (verified 2026-09-28), so the glyphs are hand-ported as ImageVectors
// from Lucide (https://lucide.dev, ISC license) path data: 24dp viewport, 2dp stroke,
// round caps/joins — visually consistent across the whole set.

private val StrokeColor = Color(0xFF000000)

private fun lucideIcon(name: String, vararg elements: String): ImageVector =
    ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply {
            elements.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(StrokeColor),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy a$r,$r 0 1 0 ${2 * r},0 a$r,$r 0 1 0 ${-2 * r},0 z"

private fun rect(topLeft: Offset, size: Size, cornerRadius: Float): String {
    val x = topLeft.x
    val y = topLeft.y
    val r = cornerRadius
    val iw = size.width - 2 * r
    val ih = size.height - 2 * r
    return "M${x + r},$y h$iw a$r,$r 0 0 1 $r,$r v$ih a$r,$r 0 0 1 ${-r},$r " +
        "h${-iw} a$r,$r 0 0 1 ${-r},${-r} v${-ih} a$r,$r 0 0 1 $r,${-r} z"
}

private val CameraIcon =
    lucideIcon(
        "Camera",
        "M13.997,4a2,2 0 0 1 1.76,1.05l0.486,0.9A2,2 0 0 0 18.003,7H20a2,2 0 0 1 2,2v9a2,2 0 0 1-2,2H4a2,2 0 0 1-2-2V9a2,2 0 0 1 2-2h1.997a2,2 0 0 0 1.759,-1.048l0.489,-0.904A2,2 0 0 1 10.004,4z",
        circle(12f, 13f, 3f),
    )

private val MicIcon =
    lucideIcon(
        "Mic",
        "M12,19v3",
        "M19,10v2a7,7 0 0 1 -14,0v-2",
        rect(Offset(9f, 2f), Size(6f, 13f), 3f),
    )

private val MicOffIcon =
    lucideIcon(
        "MicOff",
        "M12,19v3",
        "M15,9.34V5a3,3 0 0 0 -5.68,-1.33",
        "M16.95,16.95A7,7 0 0 1 5,12v-2",
        "M18.89,13.23A7,7 0 0 0 19,12v-2",
        "M2,2 L20,20",
        "M9,9v3a3,3 0 0 0 5.12,2.12",
    )

private val GamepadIcon =
    lucideIcon(
        "Gamepad",
        "M6,11h4",
        "M8,9v4",
        "M15,12h0.01",
        "M18,10h0.01",
        "M17.32,5H6.68a4,4 0 0 0 -3.978,3.59c-0.006,0.052 -0.01,0.101 -0.017,0.152C2.604,9.416 2,14.456 2,16a3,3 0 0 0 3,3c1,0 1.5,-0.5 2,-1l1.414,-1.414A2,2 0 0 1 9.828,16h4.344a2,2 0 0 1 1.414,0.586L17,18c0.5,0.5 1,1 2,1a3,3 0 0 0 3,-3c0,-1.545 -0.604,-6.584 -0.685,-7.258c-0.007,-0.05 -0.011,-0.1 -0.017,-0.151A4,4 0 0 0 17.32,5z",
    )

private val ChatIcon =
    lucideIcon(
        "Chat",
        "M22,17a2,2 0 0 1 -2,2H6.828a2,2 0 0 0 -1.414,0.586l-2.202,2.202A0.71,0.71 0 0 1 2,21.286V5a2,2 0 0 1 2,-2h16a2,2 0 0 1 2,2z",
    )

private val MusicIcon =
    lucideIcon(
        "Music",
        "M9,18V5l12,-2v13",
        circle(6f, 18f, 3f),
        circle(18f, 16f, 3f),
    )

private val MonitorIcon =
    lucideIcon(
        "Monitor",
        rect(Offset(2f, 3f), Size(20f, 14f), 2f),
        "M8,21h8",
        "M12,17v4",
    )

private val UsersIcon =
    lucideIcon(
        "Users",
        "M16,21v-2a4,4 0 0 0 -4,-4H6a4,4 0 0 0 -4,4v2",
        "M16,3.128a4,4 0 0 1 0,7.744",
        "M22,21v-2a4,4 0 0 0 -3,-3.87",
        circle(9f, 7f, 4f),
    )

private val PresentationIcon =
    lucideIcon(
        "Presentation",
        "M2,3h20",
        "M21,3v11a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V3",
        "M7,21 L12,16 L17,21",
    )

private val ImageIcon =
    lucideIcon(
        "Image",
        rect(Offset(3f, 3f), Size(18f, 18f), 2f),
        circle(9f, 9f, 2f),
        "M21,15 L17.914,11.914a2,2 0 0 0 -2.828,0L6,21",
    )

private val VideoIcon =
    lucideIcon(
        "Video",
        "M16,13 L21.223,16.482a0.5,0.5 0 0 0 0.777,-0.416V7.87a0.5,0.5 0 0 0 -0.752,-0.432L16,10.5",
        rect(Offset(2f, 6f), Size(14f, 12f), 2f),
    )

private val GlobeIcon =
    lucideIcon(
        "Globe",
        circle(12f, 12f, 10f),
        "M12,2a14.5,14.5 0 0 0 0,20a14.5,14.5 0 0 0 0,-20",
        "M2,12h20",
    )

private val HeartIcon =
    lucideIcon(
        "Heart",
        "M2,9.5a5.5,5.5 0 0 1 9.591,-3.676a0.56,0.56 0 0 0 0.818,0A5.49,5.49 0 0 1 22,9.5c0,2.29 -1.5,4 -3,5.5l-5.492,5.313a2,2 0 0 1 -3,0.019L5,15c-1.5,-1.5 -3,-3.2 -3,-5.5",
    )

private val StarIcon =
    lucideIcon(
        "Star",
        "M11.525,2.295a0.53,0.53 0 0 1 0.95,0l2.31,4.679a2.123,2.123 0 0 0 1.595,1.16l5.166,0.756a0.53,0.53 0 0 1 0.294,0.904l-3.736,3.638a2.123,2.123 0 0 0 -0.611,1.878l0.882,5.14a0.53,0.53 0 0 1 -0.771,0.56l-4.618,-2.428a2.122,2.122 0 0 0 -1.973,0L6.396,21.01a0.53,0.53 0 0 1 -0.77,-0.56l0.881,-5.139a2.122,2.122 0 0 0 -0.611,-1.879L2.16,9.795a0.53,0.53 0 0 1 0.294,-0.906l5.165,-0.755a2.122,2.122 0 0 0 1.597,-1.16z",
    )

private val BoltIcon =
    lucideIcon(
        "Bolt",
        "M15.914,4a1.5,1.5 0 0 0 -2.474,-1.561l-9,9A1.5,1.5 0 0 0 5.5,14h4.002a0.5,0.5 0 0 1 0.471,0.666L8.086,20a1.5,1.5 0 0 0 2.475,1.56l9,-9A1.5,1.5 0 0 0 18.5,10h-3.997a0.5,0.5 0 0 1 -0.472,-0.667z",
    )

private val CoffeeIcon =
    lucideIcon(
        "Coffee",
        "M10,2v2",
        "M14,2v2",
        "M16,8a1,1 0 0 1 1,1v8a4,4 0 0 1 -4,4H7a4,4 0 0 1 -4,-4V9a1,1 0 0 1 1,-1h14a4,4 0 1 1 0,8h-1",
        "M6,2v2",
    )

private val StreamIcon =
    lucideIcon(
        "Stream",
        "M4.9,16.1C1,12.2 1,5.8 4.9,1.9",
        "M7.8,4.7a6.14,6.14 0 0 0 -0.8,7.5",
        circle(12f, 9f, 2f),
        "M16.2,4.8c2,2 2.26,5.11 0.8,7.47",
        "M19.1,1.9a9.96,9.96 0 0 1 0,14.1",
        "M9.5,18h5",
        "M8,22 L12,11 L16,22",
    )

private val RecordIcon =
    lucideIcon(
        "Record",
        circle(12f, 12f, 10f),
        circle(12f, 12f, 4f),
        "M12,12h0.01",
    )

private val MixerIcon =
    lucideIcon(
        "Mixer",
        "M10,8h4",
        "M12,21v-9",
        "M12,8V3",
        "M17,16h4",
        "M19,12V3",
        "M19,21v-5",
        "M3,14h4",
        "M5,10V3",
        "M5,21v-7",
    )

private val StatsIcon =
    lucideIcon(
        "Stats",
        "M22,12h-2.48a2,2 0 0 0 -1.93,1.46l-2.35,8.36a0.25,0.25 0 0 1 -0.48,0L9.24,2.18a0.25,0.25 0 0 0 -0.48,0l-2.35,8.36A2,2 0 0 1 4.49,12H2",
    )

private val InventoryIcon =
    lucideIcon(
        "Inventory",
        "M12.83,2.18a2,2 0 0 0 -1.66,0L2.6,6.08a1,1 0 0 0 0,1.83l8.58,3.91a2,2 0 0 0 1.66,0l8.58,-3.9a1,1 0 0 0 0,-1.83z",
        "M2,12a1,1 0 0 0 0.58,0.91l8.6,3.91a2,2 0 0 0 1.65,0l8.58,-3.9A1,1 0 0 0 22,12",
        "M2,17a1,1 0 0 0 0.58,0.91l8.6,3.91a2,2 0 0 0 1.65,0l8.58,-3.9A1,1 0 0 0 22,17",
    )

private val DoctorIcon =
    lucideIcon(
        "Doctor",
        "M11,2v2",
        "M5,2v2",
        "M5,3H4a2,2 0 0 0 -2,2v4a6,6 0 0 0 12,0V5a2,2 0 0 0 -2,-2h-1",
        "M8,15a6,6 0 0 0 12,0v-3",
        circle(20f, 10f, 2f),
    )

private val GraphIcon =
    lucideIcon(
        "Graph",
        "M10.586,5.414 L5.414,10.586",
        "M18.586,13.414 L13.414,18.586",
        "M6,12h12",
        circle(12f, 20f, 2f),
        circle(12f, 4f, 2f),
        circle(20f, 12f, 2f),
        circle(4f, 12f, 2f),
    )

private val SettingsIcon =
    lucideIcon(
        "Settings",
        "M9.671,4.136a2.34,2.34 0 0 1 4.659,0a2.34,2.34 0 0 0 3.319,1.915a2.34,2.34 0 0 1 2.33,4.033a2.34,2.34 0 0 0 0,3.831a2.34,2.34 0 0 1 -2.33,4.033a2.34,2.34 0 0 0 -3.319,1.915a2.34,2.34 0 0 1 -4.659,0a2.34,2.34 0 0 0 -3.32,-1.915a2.34,2.34 0 0 1 -2.33,-4.033a2.34,2.34 0 0 0 0,-3.831A2.34,2.34 0 0 1 6.35,6.051a2.34,2.34 0 0 0 3.319,-1.915",
        circle(12f, 12f, 3f),
    )

private val ScenesIcon =
    lucideIcon(
        "Scenes",
        "M12.296,3.464 L15.316,7.42",
        "M20.2,6 L3,11 L2.1,8.6c-0.3,-1.1 0.3,-2.2 1.3,-2.5l13.5,-4c1.1,-0.3 2.2,0.3 2.5,1.3z",
        "M3,11h18v8a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2z",
        "M6.18,5.276 L9.28,9.175",
    )

private val FilmIcon =
    lucideIcon(
        "Film",
        rect(Offset(3f, 3f), Size(18f, 18f), 2f),
        "M7,3v18",
        "M3,7.5h4",
        "M3,12h18",
        "M3,16.5h4",
        "M17,3v18",
        "M17,7.5h4",
        "M17,16.5h4",
    )

private val HeadphonesIcon =
    lucideIcon(
        "Headphones",
        "M3,14h3a2,2 0 0 1 2,2v3a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2v-7a9,9 0 0 1 18,0v7a2,2 0 0 1 -2,2h-1a2,2 0 0 1 -2,-2v-3a2,2 0 0 1 2,-2h3",
    )

private val BellIcon =
    lucideIcon(
        "Bell",
        "M10.268,21a2,2 0 0 0 3.464,0",
        "M3.262,15.326A1,1 0 0 0 4,17h16a1,1 0 0 0 0.74,-1.673C19.41,13.956 18,12.499 18,8A6,6 0 0 0 6,8c0,4.499 -1.411,5.956 -2.738,7.326",
    )

private val SparklesIcon =
    lucideIcon(
        "Sparkles",
        "M11.017,2.814a1,1 0 0 1 1.966,0l1.051,5.558a2,2 0 0 0 1.594,1.594l5.558,1.051a1,1 0 0 1 0,1.966l-5.558,1.051a2,2 0 0 0 -1.594,1.594l-1.051,5.558a1,1 0 0 1 -1.966,0l-1.051,-5.558a2,2 0 0 0 -1.594,-1.594l-5.558,-1.051a1,1 0 0 1 0,-1.966l5.558,-1.051a2,2 0 0 0 1.594,-1.594z",
        "M20,2v4",
        "M22,4h-4",
        circle(4f, 20f, 2f),
    )

private val CastIcon =
    lucideIcon(
        "Cast",
        "M2,8V6a2,2 0 0 1 2,-2h16a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-6",
        "M2,12a9,9 0 0 1 8,8",
        "M2,16a5,5 0 0 1 4,4",
        "M2,20h0.01",
    )

private val LockIcon =
    lucideIcon(
        "Lock",
        rect(Offset(3f, 11f), Size(18f, 11f), 2f),
        "M7,11V7a5,5 0 0 1 10,0v4",
    )

private val LockOpenIcon =
    lucideIcon(
        "LockOpen",
        rect(Offset(3f, 11f), Size(18f, 11f), 2f),
        "M7,11V7a5,5 0 0 1 9.9,-1",
    )

/**
 * User-assignable scene icon catalogue (parity with the desktop's curated glyphs,
 * docs/DESIGN_SYSTEM.md §6). Render with `Icon(SceneIcon.CAMERA.imageVector, …)`.
 */
enum class SceneIcon {
    CAMERA,
    MIC,
    MIC_OFF,
    GAMEPAD,
    CHAT,
    MUSIC,
    MONITOR,
    USERS,
    PRESENTATION,
    IMAGE,
    VIDEO,
    GLOBE,
    HEART,
    STAR,
    BOLT,
    COFFEE,
    STREAM,
    RECORD,
    MIXER,
    STATS,
    INVENTORY,
    DOCTOR,
    GRAPH,
    SETTINGS,
    SCENES,
    FILM,
    HEADPHONES,
    BELL,
    SPARKLES,
    CAST,
    LOCK,
    LOCK_OPEN,
}

/** The [ImageVector] for a catalogue entry (Lucide style: 24dp, 2dp rounded stroke). */
val SceneIcon.imageVector: ImageVector
    get() =
        when (this) {
            SceneIcon.CAMERA -> CameraIcon
            SceneIcon.MIC -> MicIcon
            SceneIcon.MIC_OFF -> MicOffIcon
            SceneIcon.GAMEPAD -> GamepadIcon
            SceneIcon.CHAT -> ChatIcon
            SceneIcon.MUSIC -> MusicIcon
            SceneIcon.MONITOR -> MonitorIcon
            SceneIcon.USERS -> UsersIcon
            SceneIcon.PRESENTATION -> PresentationIcon
            SceneIcon.IMAGE -> ImageIcon
            SceneIcon.VIDEO -> VideoIcon
            SceneIcon.GLOBE -> GlobeIcon
            SceneIcon.HEART -> HeartIcon
            SceneIcon.STAR -> StarIcon
            SceneIcon.BOLT -> BoltIcon
            SceneIcon.COFFEE -> CoffeeIcon
            SceneIcon.STREAM -> StreamIcon
            SceneIcon.RECORD -> RecordIcon
            SceneIcon.MIXER -> MixerIcon
            SceneIcon.STATS -> StatsIcon
            SceneIcon.INVENTORY -> InventoryIcon
            SceneIcon.DOCTOR -> DoctorIcon
            SceneIcon.GRAPH -> GraphIcon
            SceneIcon.SETTINGS -> SettingsIcon
            SceneIcon.SCENES -> ScenesIcon
            SceneIcon.FILM -> FilmIcon
            SceneIcon.HEADPHONES -> HeadphonesIcon
            SceneIcon.BELL -> BellIcon
            SceneIcon.SPARKLES -> SparklesIcon
            SceneIcon.CAST -> CastIcon
            SceneIcon.LOCK -> LockIcon
            SceneIcon.LOCK_OPEN -> LockOpenIcon
        }
