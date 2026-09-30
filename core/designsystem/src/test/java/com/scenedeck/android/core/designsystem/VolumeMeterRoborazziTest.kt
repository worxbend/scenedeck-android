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
import com.scenedeck.android.core.designsystem.components.MeterLevelsHolder
import com.scenedeck.android.core.designsystem.components.VolumeMeter
import com.scenedeck.android.core.designsystem.components.dbToMul
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ChannelLevels
import com.scenedeck.android.core.model.VolumeMeterReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots for VolumeMeter levels/states (dark, motion OFF for determinism). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class VolumeMeterRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    private fun holderWith(vararg channelDb: Float): MeterLevelsHolder {
        val holder = MeterLevelsHolder()
        holder.reading.value =
            VolumeMeterReading(
                inputName = "Test",
                channels =
                    channelDb.map { db ->
                        ChannelLevels(
                            magnitudeMul = dbToMul(db),
                            peakMul = dbToMul(db),
                            inputPeakMul = dbToMul(db),
                        )
                    },
            )
        return holder
    }

    private fun golden(name: String, holder: MeterLevelsHolder, muted: Boolean = false) {
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    VolumeMeter(
                        levelsHolder = holder,
                        faderMul = 1.0,
                        muted = muted,
                        motionLevel = MotionLevel.OFF,
                        modifier = Modifier.width(120.dp).height(220.dp).padding(8.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun meterMinus40Stereo() {
        golden("volume_meter_minus40_dark.png", holderWith(-40f, -40f))
    }

    @Test
    fun meterMinus15Stereo() {
        golden("volume_meter_minus15_dark.png", holderWith(-15f, -15f))
    }

    @Test
    fun meterMinus6Stereo() {
        golden("volume_meter_minus6_dark.png", holderWith(-6f, -6f))
    }

    @Test
    fun meterMuted() {
        golden("volume_meter_muted_dark.png", holderWith(-15f, -15f), muted = true)
    }

    @Test
    fun meterMono() {
        golden("volume_meter_mono_dark.png", holderWith(-10f))
    }

    @Test
    fun meterEmptyChannels() {
        golden("volume_meter_empty_dark.png", MeterLevelsHolder())
    }
}
