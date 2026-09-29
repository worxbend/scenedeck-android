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
import com.scenedeck.android.core.designsystem.components.MixerStrip
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.MixerScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shots for MixerStrip (fader/meter/mute/lock) across the theme matrix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MixerStripRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        family: ThemeFamily,
        darkTheme: Boolean,
        name: String,
        muted: Boolean = false,
        locked: Boolean = false,
    ) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = darkTheme) {
                Surface {
                    MixerStrip(
                        name = "Test Tone 440",
                        scope = MixerScope.SCENE,
                        volumeMul = 0.7,
                        muted = muted,
                        locked = locked,
                        meterHolder = MeterLevelsHolder(),
                        motionLevel = MotionLevel.OFF,
                        hapticsEnabled = false,
                        onVolumePreview = {},
                        onVolumeCommit = {},
                        onToggleMute = {},
                        onToggleLock = {},
                        modifier = Modifier
                            .width(200.dp)
                            .height(420.dp)
                            .padding(8.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun stripSceneDeckDark() = capture(ThemeFamily.SCENEDECK, true, "mixer_strip_scenedeck_dark.png")

    @Test
    fun stripLockedShowsLockGlyph() =
        capture(ThemeFamily.SCENEDECK, true, "mixer_strip_locked_dark.png", locked = true)

    @Test
    fun stripMutedDark() = capture(ThemeFamily.SCENEDECK, true, "mixer_strip_muted_dark.png", muted = true)

    @Test
    fun stripObsDark() = capture(ThemeFamily.OBS, true, "mixer_strip_obs_dark.png")

    @Test
    fun stripNordDark() = capture(ThemeFamily.NORD, true, "mixer_strip_nord_dark.png")

    @Test
    fun stripHighContrastDark() =
        capture(ThemeFamily.HIGH_CONTRAST, true, "mixer_strip_high_contrast_dark.png")

    @Test
    fun stripSceneDeckLight() = capture(ThemeFamily.SCENEDECK, false, "mixer_strip_scenedeck_light.png")
}
