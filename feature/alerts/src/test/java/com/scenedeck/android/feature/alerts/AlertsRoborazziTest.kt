package com.scenedeck.android.feature.alerts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.LiveAlert
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import java.util.Locale
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
class AlertsRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val locale = Locale.getDefault()

    @Before
    fun fixedLocale() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(locale)
    }

    private fun fixture(
        family: ThemeFamily,
        dark: Boolean,
        onNavigate: (AlertDestination) -> Unit = {},
        onDismiss: (LiveAlert) -> Unit = {},
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    AlertsContent(alertsPreviewState(), onNavigate, onDismiss, { true }, {}, {})
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean) {
        fixture(family, dark)
        compose.onNodeWithText("Rendering is taking too long").assertIsDisplayed()
        compose
            .onNodeWithText("The last warning has not been confirmed as recovered.")
            .assertIsDisplayed()
        compose
            .onRoot()
            .captureRoboImage(
                "alerts_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun warningActionsKeepTheirOriginalIdentityAndNavigateToReadings() {
        val destinations = mutableListOf<AlertDestination>()
        val dismissed = mutableListOf<LiveAlert>()
        fixture(ThemeFamily.SCENEDECK, true, { destinations += it }, { dismissed += it })
        compose.onAllNodesWithText("Open Stats")[0].performClick()
        compose.onAllNodesWithText("Dismiss for 60 seconds")[1].performClick()
        assertEquals(listOf(AlertDestination.STATS), destinations)
        assertEquals(listOf(alertsPreviewState().alerts[1]), dismissed)
        compose.onNodeWithTag("alerts").performScrollToNode(hasText("Open Preflight"))
        compose.onAllNodesWithText("Open Preflight")[0].performClick()
        assertEquals(AlertDestination.PREFLIGHT, destinations.last())
    }
}
