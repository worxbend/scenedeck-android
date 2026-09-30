package com.scenedeck.android.core.designsystem

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.TrendChart
import com.scenedeck.android.core.designsystem.components.TrendSamplesHolder
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots for TrendChart population states (dark, motion OFF for determinism). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TrendChartRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    private fun golden(name: String, samples: List<Float>) {
        val holder = TrendSamplesHolder()
        holder.samples.value = samples
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    TrendChart(
                        samplesHolder = holder,
                        motionLevel = MotionLevel.OFF,
                        modifier = Modifier.width(320.dp).height(140.dp).padding(8.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun chartPopulated() {
        // Full 120-sample window: 60 fps baseline with a slow sine wobble + dip.
        val samples =
            (0 until 120).map { i ->
                (59f + sin(i / 9.0).toFloat() + if (i in 80..95) -14f else 0f)
            }
        golden("trend_chart_populated_dark.png", samples)
    }

    @Test
    fun chartSparse() {
        // Right-aligned partial window (page just opened).
        golden("trend_chart_sparse_dark.png", listOf(58f, 59.5f, 59f, 60f, 57f, 59f, 59.8f))
    }

    @Test
    fun chartEmpty() {
        golden("trend_chart_empty_dark.png", emptyList())
    }
}
