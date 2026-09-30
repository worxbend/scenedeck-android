package com.scenedeck.android.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Widget content assertions via glance-appwidget-testing. Pixel goldens are skipped: neither
 * glance-appwidget-testing 1.2.0 nor roborazzi 1.75.0 ship a Glance bitmap capture path (verified
 * 2026-09-29), so widget visuals are covered by M8b's emulator screenshots instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SceneDeckWidgetTest {

    @Test
    fun emptyStateShowsOpenAppHint() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 140.dp))
        provideComposable {
            GlanceTheme { WidgetContent(WidgetSnapshot()) }
        }
        onNode(hasText("No scenes yet")).assertExists()
        onNode(hasText("Tap to open SceneDeck")).assertExists()
    }

    @Test
    fun populatedShowsScenesAndConnectionLabel() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(360.dp, 320.dp))
        provideComposable {
            GlanceTheme {
                WidgetContent(
                    WidgetSnapshot(
                        sceneNames = listOf("Cam 1", "Screen", "Quiet B", "Scene"),
                        programScene = "Cam 1",
                        connectionLabel = "Live",
                    )
                )
            }
        }
        onNode(hasText("SceneDeck · Live")).assertExists()
        // LazyVerticalGrid only materializes the visible slots in the unit-test env.
        onNode(hasText("Cam 1")).assertExists()
        onNode(hasText("Screen")).assertExists()
    }
}
