package com.scenedeck.android.feature.preflight

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.PreflightSetupState
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PreflightRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val originalZone = TimeZone.getDefault()
    private val originalLocale = Locale.getDefault()

    @Before
    fun stableTimestampLocale() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreTimestampLocale() {
        TimeZone.setDefault(originalZone)
        Locale.setDefault(originalLocale)
    }

    private fun fixture(family: ThemeFamily, dark: Boolean, onRun: (Boolean) -> Unit = {}) {
        val state = preflightPreviewState()
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    PreflightContent(
                        state,
                        state.setup as PreflightSetupState.Ready,
                        onRun,
                        {},
                        {},
                        { _, _ -> },
                    )
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean) {
        fixture(family, dark)
        compose
            .onNodeWithContentDescription("Fail. Microphone control. Mic/Aux is muted.")
            .assertIsDisplayed()
        compose
            .onNodeWithContentDescription(
                "Unknown. Recording disk space. Free space is unavailable."
            )
            .assertIsDisplayed()
        compose
            .onRoot()
            .captureRoboImage(
                "preflight_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun signalConsentIsOnlyForOneRun() {
        val runs = mutableListOf<Boolean>()
        fixture(ThemeFamily.SCENEDECK, true) { runs += it }
        compose.onNodeWithText("Check microphone signal this time").performClick()
        compose.onNodeWithText("Run checks").performClick()
        compose.onNodeWithText("Run checks").performClick()
        assertEquals(listOf(true, false), runs)
    }
}
