package com.scenedeck.android.feature.live

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.SceneCardState
import com.scenedeck.android.core.data.SceneRole
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class QuickEditAliasRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(family: ThemeFamily, dark: Boolean) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    QuickEditSheet(
                        SceneCardState(
                            "OBS Camera",
                            SceneRole.ARCHIVE,
                            null,
                            null,
                            3,
                            false,
                            localAlias = "Host",
                        ),
                        onDismiss = {},
                        onSave = { false },
                    )
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean) {
        fixture(family, dark)
        compose.onNodeWithText("OBS · OBS Camera").assertIsDisplayed()
        compose
            .onRoot()
            .captureRoboImage(
                "alias_quick_edit_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun failedSaveRetainsAliasDraft() {
        fixture(ThemeFamily.SCENEDECK, true)
        compose.onNodeWithText("Local label").performTextReplacement("Guest")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Couldn't save scene details. Reopen this editor if the scene or workspace changed."
            )
            .assertExists()
        compose.onNodeWithText("Guest").assertExists()
    }
}
