package com.scenedeck.android.feature.inventory

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.SceneRegistryEntry
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
// Dialog + text-field layout loops with explicit widths: robolectric/robolectric#8460.
// Keep the real dialog/input but use Robolectric's default 320dp window width.
@Config(sdk = [34], qualifiers = "h891dp-xhdpi")
class SceneAliasDialogRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(family: ThemeFamily, dark: Boolean) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    SceneAliasDialog(
                        InventoryScene(
                            "OBS Camera",
                            SceneRegistryEntry(
                                "OBS Camera",
                                SceneRole.ARCHIVE,
                                localAlias = "Host",
                            ),
                            false,
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
                "alias_editor_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun failedSaveKeepsTheDraft() {
        fixture(ThemeFamily.SCENEDECK, true)
        compose.onNodeWithText("Local label").performTextReplacement("Guest")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Guest").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Couldn't save this label. Reopen the editor if the scene or workspace changed."
            )
            .assertIsDisplayed()
    }
}
