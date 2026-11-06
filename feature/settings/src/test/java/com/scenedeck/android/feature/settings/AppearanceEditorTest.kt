package com.scenedeck.android.feature.settings

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceEditorTest {
    @get:Rule val compose = createComposeRule()
    private var draft by mutableStateOf(AppearanceSettings(mode = DarkMode.DARK))
    private var applied = 0
    private var canceled = 0

    @Test fun sceneDeckDark() = render(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = render(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = render(ThemeFamily.OBS, true)

    @Test fun obsLight() = render(ThemeFamily.OBS, false)

    @Test
    @Config(qualifiers = "w1000dp-h800dp-mdpi")
    fun tablet() = render(ThemeFamily.SCENEDECK, true, suffix = "tablet")

    @Test
    @Config(qualifiers = "w760dp-h340dp-land-mdpi")
    fun compact() = render(ThemeFamily.SCENEDECK, true, 1.3f, "compact")

    @Test fun largeText() = render(ThemeFamily.SCENEDECK, false, 2f, "large_text")

    @Test
    fun paletteAccentAndResetOnlyApplyOnExplicitAction() {
        render(ThemeFamily.SCENEDECK, true, suffix = "actions")
        compose.onNodeWithText("Choose palette · SceneDeck").performScrollTo().performClick()
        compose.onNodeWithText("Ocean").performScrollTo().performClick()
        assertEquals(ThemeFamily.OCEAN, draft.family)
        compose.onNodeWithText("Violet").performScrollTo().performClick()
        assertEquals(ThemeAccent.VIOLET, draft.accent)
        assertEquals(0, applied)
        compose.onNodeWithText("Apply").performClick()
        assertEquals(1, applied)
        compose.onNodeWithText("Reset defaults").performClick()
        assertEquals(AppearanceSettings(), draft)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, canceled)
    }

    @Test
    fun highContrastProtectsAccentChoices() {
        draft = AppearanceSettings(family = ThemeFamily.HIGH_CONTRAST)
        render(ThemeFamily.HIGH_CONTRAST, true, suffix = "contrast")
        compose.onNodeWithText("Violet").performScrollTo().assertIsNotEnabled()
        compose
            .onNodeWithText("High Contrast keeps its own accent and contrast surfaces.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun render(
        family: ThemeFamily,
        dark: Boolean,
        scale: Float = 1f,
        suffix: String = "${family.name.lowercase()}_${if (dark) "dark" else "light"}",
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface {
                        AppearanceEditorContent(
                            draft,
                            false,
                            null,
                            { draft = it(draft) },
                            { applied++ },
                            { canceled++ },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Apply").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onRoot().captureRoboImage("appearance_editor_$suffix.png")
    }
}
