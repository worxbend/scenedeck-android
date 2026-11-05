package com.scenedeck.android.feature.preflight

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.PreflightConfig
import com.scenedeck.android.core.data.PreflightSetupState
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PreflightSetupRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private var saved: PreflightConfig? = null

    private fun fixture(family: ThemeFamily, dark: Boolean) {
        val state = preflightPreviewState()
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    PreflightSetupContent(
                        (state.setup as PreflightSetupState.Ready).config,
                        state.scenes,
                        state.microphones,
                        {},
                        {
                            saved = it
                            false
                        },
                        Modifier.padding(20.dp),
                    )
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean) {
        fixture(family, dark)
        compose.onNodeWithText("Host").assertIsDisplayed()
        compose.onNodeWithText("OBS · Camera").assertIsDisplayed()
        compose
            .onRoot()
            .captureRoboImage(
                "preflight_setup_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun failedSaveRetainsDraftAndSceneSelectionUsesObsName() {
        fixture(ThemeFamily.SCENEDECK, true)
        compose.onNodeWithText("Expected OBS collection").performTextReplacement("Travel")
        compose.onNodeWithText("Save setup").performClick()
        compose.onNodeWithText("Travel").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Couldn't save setup. Check the fields, or reopen if the workspace or checklist changed."
            )
            .assertIsDisplayed()
        assertEquals(listOf("Camera"), saved?.requiredScenes)
        assertEquals("Travel", saved?.expectedCollection)
        compose.onNodeWithText("Minimum free disk space (MB)").performTextReplacement("bad")
        compose.onNodeWithText("Save setup").assertIsNotEnabled()
    }

    @Test
    fun expandedSheetKeepsSaveVisibleWhileManualEditorScrolls() {
        val state = preflightPreviewState()
        compose.setContent {
            SceneDeckTheme {
                PreflightSetupSheet(
                    (state.setup as PreflightSetupState.Ready).config,
                    state.scenes,
                    state.microphones,
                    {},
                    { true },
                )
            }
        }
        compose.onNodeWithText("Add manual check").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save setup").assertIsDisplayed()
    }
}
