package com.scenedeck.android.feature.live

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.SceneThumbnail
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AliasPreviewRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean) {
        val bitmap =
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xFF336699.toInt())
            }
        val previews =
            ScenePreviews(
                mapOf(
                    "Camera" to SceneThumbnail(bitmap, 29_000),
                    "Wide" to SceneThumbnail(bitmap, 0),
                ),
                setOf("Missing"),
                30_000,
                true,
                true,
            )
        val offline = previews.copy(connected = false)
        assertNull(offline.image("Camera"))
        val hidden = previews.copy(enabled = false)
        assertNull(hidden.image("Camera"))
        assertNull(hidden.label("Camera"))
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    PreviewExamples(previews)
                }
            }
        }
        compose.onNodeWithText("Preview · 1s ago").assertIsDisplayed()
        compose.onNodeWithText("Stale preview · 30s ago").assertIsDisplayed()
        compose.onNodeWithText("Preview unavailable").assertIsDisplayed()
        compose.onNodeWithText("Preview pending").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("OBS scene: Camera. Preview · 1s ago", substring = true)
            .assertIsDisplayed()
        compose
            .onRoot()
            .captureRoboImage(
                "alias_previews_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @androidx.compose.runtime.Composable
    private fun PreviewExamples(previews: ScenePreviews) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SceneCard(
                    "Host",
                    SceneDeckIcons.Scenes,
                    false,
                    modifier = Modifier.weight(1f),
                    obsName = "Camera",
                    thumbnail = previews.image("Camera")?.asImageBitmap(),
                    previewStatus = previews.label("Camera"),
                    motionLevel = MotionLevel.OFF,
                )
                SceneCard(
                    "Wide",
                    SceneDeckIcons.Scenes,
                    false,
                    modifier = Modifier.weight(1f),
                    thumbnail = previews.image("Wide")?.asImageBitmap(),
                    previewStatus = previews.label("Wide"),
                    motionLevel = MotionLevel.OFF,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SceneCard(
                    "Missing",
                    SceneDeckIcons.Scenes,
                    false,
                    modifier = Modifier.weight(1f),
                    previewStatus = previews.label("Missing"),
                    motionLevel = MotionLevel.OFF,
                )
                SceneCard(
                    "Next",
                    SceneDeckIcons.Scenes,
                    false,
                    modifier = Modifier.weight(1f),
                    thumbnail = previews.image("Next")?.asImageBitmap(),
                    previewStatus = previews.label("Next"),
                    motionLevel = MotionLevel.OFF,
                )
            }
        }
    }
}
