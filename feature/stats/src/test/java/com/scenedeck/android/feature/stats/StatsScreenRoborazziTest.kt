package com.scenedeck.android.feature.stats

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.TrendSamplesHolder
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots of the stats page (dark, motion OFF for determinism). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatsScreenRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun holderWith(samples: List<Float>): TrendSamplesHolder {
        val holder = TrendSamplesHolder()
        holder.samples.value = samples
        return holder
    }

    @Test
    fun statsScreenPopulatedDark() {
        val fps = (0 until 120).map { i -> 59f + sin(i / 9.0).toFloat() + if (i in 80..95) -8f else 0f }
        val render = (0 until 120).map { i -> 1.2f + sin(i / 7.0).toFloat() * 0.4f + if (i in 80..95) 4f else 0f }
        val drops = FrameDropSeriesHolder().apply {
            renderSkipped.value = (0 until 120).map { i -> if (i % 17 == 0) 2f else 0f }
            outputSkipped.value = (0 until 120).map { i -> if (i in 80..95 && i % 3 == 0) 3f else 0f }
        }
        val state = StatsUiState(
            connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
            sampleCount = 120,
            fps = 59.9f,
            renderTimeMs = 1.4f,
            droppedPct = 2.4f,
            congestionPct = 42f,
            cpuUsagePct = 12.5,
            memoryUsageMb = 512.0,
            bitrateKbps = 6_012,
            renderTotalFrames = 431_213,
            renderSkippedFrames = 138,
            outputTotalFrames = 428_990,
            outputSkippedFrames = 10_342,
            streamBytes = 328_400_000,
            recordBytes = 1_120_500_000,
            streamActive = true,
            recordActive = true,
        )
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    StatsContent(
                        uiState = state,
                        fpsTrend = holderWith(fps),
                        renderTrend = holderWith(render),
                        frameDrops = drops,
                        motionLevel = MotionLevel.OFF,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("stats_screen_populated_dark.png")
    }

    @Test
    fun statsScreenEmptyDark() {
        val state = StatsUiState(
            connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
        )
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    StatsContent(
                        uiState = state,
                        fpsTrend = TrendSamplesHolder(),
                        renderTrend = TrendSamplesHolder(),
                        frameDrops = FrameDropSeriesHolder(),
                        motionLevel = MotionLevel.OFF,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("stats_screen_empty_dark.png")
    }
}
