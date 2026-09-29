package com.scenedeck.android.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.components.GaugeDirection
import com.scenedeck.android.core.designsystem.components.StatGauge
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots for StatGauge zone states (dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatGaugeRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun golden(name: String, droppedPct: Float, congestionPct: Float, fps: Float) {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    Row(Modifier.padding(8.dp)) {
                        StatGauge(
                            value = droppedPct,
                            minValue = 0f,
                            maxValue = 10f,
                            warnThreshold = 1f,
                            critThreshold = 5f,
                            label = "Dropped",
                            unit = "%",
                            modifier = Modifier
                                .size(96.dp)
                                .padding(4.dp),
                        )
                        StatGauge(
                            value = congestionPct,
                            minValue = 0f,
                            maxValue = 100f,
                            warnThreshold = 30f,
                            critThreshold = 60f,
                            label = "Congestion",
                            unit = "%",
                            valueText = "%.0f".format(congestionPct),
                            modifier = Modifier
                                .size(96.dp)
                                .padding(4.dp),
                        )
                        StatGauge(
                            value = fps,
                            minValue = 0f,
                            maxValue = 60f,
                            warnThreshold = 54f,
                            critThreshold = 45f,
                            label = "FPS",
                            unit = "fps",
                            direction = GaugeDirection.FALLING,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun gaugesNormal() {
        golden("stat_gauge_normal_dark.png", droppedPct = 0.2f, congestionPct = 12f, fps = 59.9f)
    }

    @Test
    fun gaugesWarning() {
        golden("stat_gauge_warn_dark.png", droppedPct = 2.5f, congestionPct = 45f, fps = 50f)
    }

    @Test
    fun gaugesCritical() {
        golden("stat_gauge_crit_dark.png", droppedPct = 7.5f, congestionPct = 80f, fps = 30f)
    }
}
