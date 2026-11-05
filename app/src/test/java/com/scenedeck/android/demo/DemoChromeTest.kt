package com.scenedeck.android.demo

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.LocalSimulation
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.RecordStatus
import com.scenedeck.android.core.model.StreamStatus
import com.scenedeck.android.ui.components.StatusStrip
import com.scenedeck.android.ui.components.StatusStripState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    @Config(qualifiers = "w840dp-h360dp-land-mdpi")
    fun compactLargeTextKeepsExitAndEntryDecisionsReachable() =
        capture(ThemeFamily.SCENEDECK, true, 1.3f, "compact_large_text", compact = true)

    @Test
    fun largeTextKeepsSimulationLabelAndExitVisible() =
        capture(ThemeFamily.SCENEDECK, false, 2f, "large_text")

    private fun capture(
        family: ThemeFamily,
        dark: Boolean,
        fontScale: Float = 1f,
        name: String? = null,
        compact: Boolean = false,
    ) {
        var entry by mutableStateOf(true)
        var restricted by mutableStateOf(false)
        var opened = 0
        var exited = 0
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalSimulation provides true,
                ) {
                    if (entry)
                        DemoEntryDialog(
                            {
                                opened++
                                entry = false
                            },
                            { entry = false },
                        )
                    else
                        DemoFrame(DemoPhase.READY, { exited++ }, {}) {
                            if (restricted) DemoUnavailable(onExit = { exited++ })
                            else
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    StatusStrip(
                                        sampleStatus(),
                                        animateConnection = false,
                                        compact = compact,
                                    )
                                }
                        }
                }
            }
        }
        val suffix = name ?: "${family.name.lowercase()}_${if (dark) "dark" else "light"}"
        compose.onNodeWithText("Open demo").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage("demo_entry_$suffix.png")
        compose.onNodeWithText("Open demo").performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("DEMO · Local simulation").assertIsDisplayed()
        compose.onNodeWithText("Exit demo").assertIsDisplayed()
        compose.onRoot().captureRoboImage("demo_frame_$suffix.png")
        compose.onNodeWithText("Exit demo").performClick()
        assertEquals(1, exited)
        compose.runOnIdle { restricted = true }
        compose.onAllNodesWithText("Exit demo")[0].assertIsDisplayed()
        compose.onNodeWithText("Local demo").assertIsDisplayed()
        compose.onRoot().captureRoboImage("demo_restricted_$suffix.png")
    }

    private fun sampleStatus() =
        StatusStripState(
            connection = ConnectionState.Ready(ObsVersionInfo("Demo", "5", 1)),
            stream = StreamStatus(true, false, "00:02:15", 135_000, 1_000, 0.0, 0, 100),
            record = RecordStatus(true, false, "00:01:30", 90_000, 1_000),
        )
}
