package com.scenedeck.android.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots for SceneCard states (docs/DESIGN_SYSTEM.md §7) in dark mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SceneCardRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sceneCardReadyDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    SceneCard(
                        label = "Cam 1",
                        icon = SceneIcon.CAMERA.imageVector,
                        active = false,
                        modifier = Modifier
                            .width(200.dp)
                            .padding(16.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("scene_card_ready_dark.png")
    }

    @Test
    fun sceneCardActiveDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    SceneCard(
                        label = "Scene",
                        icon = SceneDeckIcons.Scenes,
                        active = true,
                        modifier = Modifier
                            .width(200.dp)
                            .padding(16.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("scene_card_active_dark.png")
    }

    @Test
    fun sceneCardAccentPendingDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    SceneCard(
                        label = "Screen",
                        icon = SceneIcon.MONITOR.imageVector,
                        active = false,
                        accentColor = Color(0xFF7E57C2),
                        pending = true,
                        modifier = Modifier
                            .width(200.dp)
                            .padding(16.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("scene_card_accent_pending_dark.png")
    }

    @Test
    fun sceneCardActiveObsFamilyDark() {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.OBS, darkTheme = true) {
                Surface {
                    SceneCard(
                        label = "Starting Soon",
                        icon = SceneIcon.STAR.imageVector,
                        active = true,
                        modifier = Modifier
                            .width(200.dp)
                            .padding(16.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("scene_card_active_obs_dark.png")
    }
}
