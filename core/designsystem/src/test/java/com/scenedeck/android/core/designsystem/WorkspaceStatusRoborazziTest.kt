package com.scenedeck.android.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.WorkspaceStatus
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
class WorkspaceStatusRoborazziTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    private fun capture(family: ThemeFamily, dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    Column {
                        WorkspaceStatus(false, {})
                        WorkspaceStatus(true, {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose
            .onRoot()
            .captureRoboImage(
                "workspace_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test
    fun failureOffersRetryWithoutReportingAnEmptyCollection() {
        var retried = 0
        compose.setContent { SceneDeckTheme { WorkspaceStatus(true, { retried++ }) } }
        compose.onNodeWithText("Couldn't load this workspace").assertExists()
        compose.onNodeWithText("Retry workspace").performClick()
        assertEquals(1, retried)
    }
}
