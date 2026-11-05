package com.scenedeck.android.shortcuts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.ShortcutAction
import com.scenedeck.android.core.data.ShortcutBinding
import com.scenedeck.android.core.data.ShortcutChord
import com.scenedeck.android.core.data.ShortcutPhase
import com.scenedeck.android.core.data.ShortcutRunState
import com.scenedeck.android.core.data.ShortcutTarget
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.designsystem.components.LocalSimulation
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
class ShortcutConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private val run =
        ShortcutRunState(
            "invocation",
            TEST_WORKSPACE,
            ShortcutBinding(
                TEST_WORKSPACE.key,
                "binding",
                0,
                ShortcutChord("F8"),
                ShortcutAction.Recording(false),
                true,
            ),
            ShortcutPhase.CONFIRMATION,
            ShortcutTarget.Recording(false),
        )

    private fun capture(
        family: ThemeFamily,
        dark: Boolean,
        fontScale: Float = 1f,
        name: String? = null,
        simulation: Boolean = false,
    ) {
        var confirmed = 0
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalSimulation provides simulation,
                ) {
                    ShortcutConfirmation(run, { confirmed++ }, {})
                }
            }
        }
        compose.onNodeWithText("Stop recording?").assertIsDisplayed()
        compose.onNodeWithText("Stop recording").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        if (simulation) compose.onNodeWithText("DEMO · Local simulation only").assertIsDisplayed()
        val suffix = name ?: "${family.name.lowercase()}_${if (dark) "dark" else "light"}"
        compose.onNode(isDialog()).captureRoboImage("shortcut_confirmation_$suffix.png")
        compose.onNodeWithText("Stop recording").performClick()
        assertEquals(1, confirmed)
    }

    @Test fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun demoSceneDeckDark() =
        capture(ThemeFamily.SCENEDECK, true, name = "demo_scenedeck_dark", simulation = true)

    @Test
    fun demoSceneDeckLight() =
        capture(ThemeFamily.SCENEDECK, false, name = "demo_scenedeck_light", simulation = true)

    @Test
    fun demoObsDark() = capture(ThemeFamily.OBS, true, name = "demo_obs_dark", simulation = true)

    @Test
    fun demoObsLight() = capture(ThemeFamily.OBS, false, name = "demo_obs_light", simulation = true)

    @Test
    @Config(qualifiers = "w840dp-h360dp-land-mdpi")
    fun compactWithLargeTextKeepsBothDecisionsVisible() =
        capture(ThemeFamily.SCENEDECK, true, 1.3f, "compact_large_font")
}
