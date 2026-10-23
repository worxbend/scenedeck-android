package com.scenedeck.android.core.designsystem

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.scenedeck.android.core.designsystem.components.GaugeDirection
import com.scenedeck.android.core.designsystem.components.MeterLevelsHolder
import com.scenedeck.android.core.designsystem.components.MixerStrip
import com.scenedeck.android.core.designsystem.components.SceneCard
import com.scenedeck.android.core.designsystem.components.SceneCardMode
import com.scenedeck.android.core.designsystem.components.StatGauge
import com.scenedeck.android.core.designsystem.components.sceneCardCaption
import com.scenedeck.android.core.designsystem.components.sceneCardDescription
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.MixerScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Accessibility semantics contracts (docs/DESIGN_SYSTEM.md §10). */
private fun hasSetProgress() =
    SemanticsMatcher("has SetProgress action") { it.config.contains(SemanticsActions.SetProgress) }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class A11ySemanticsTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun gaugeExposesLabelValueAndZone() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                StatGauge(
                    value = 7f,
                    minValue = 0f,
                    maxValue = 10f,
                    warnThreshold = 3f,
                    critThreshold = 5f,
                    label = "Dropped frames",
                    unit = "%",
                )
            }
        }
        composeRule.onNodeWithContentDescription("Dropped frames: 7.0 %, critical").assertExists()
    }

    @Test
    fun fallingDirectionGaugeAnnouncesZone() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                StatGauge(
                    value = 59.9f,
                    minValue = 0f,
                    maxValue = 60f,
                    warnThreshold = 54f,
                    critThreshold = 45f,
                    label = "FPS",
                    unit = "",
                    direction = GaugeDirection.FALLING,
                )
            }
        }
        composeRule.onNodeWithContentDescription("FPS: 59.9, normal").assertExists()
    }

    @Test
    fun faderIsScreenReaderAdjustable() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                MixerStrip(
                    name = "Mic/Aux",
                    scope = MixerScope.GLOBAL,
                    volumeMul = 0.8,
                    muted = false,
                    locked = false,
                    meterHolder = MeterLevelsHolder(),
                    motionLevel = MotionLevel.OFF,
                    hapticsEnabled = false,
                    onVolumePreview = {},
                    onVolumeCommit = {},
                    onToggleMute = {},
                    onToggleLock = {},
                )
            }
        }
        composeRule
            .onNode(hasContentDescription("Volume fader for Mic/Aux, -1.9 dB"))
            .assert(hasSetProgress())
    }

    @Test
    fun lockedFaderHasNoProgressAction() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                MixerStrip(
                    name = "Mic/Aux",
                    scope = MixerScope.GLOBAL,
                    volumeMul = 0.8,
                    muted = false,
                    locked = true,
                    meterHolder = MeterLevelsHolder(),
                    motionLevel = MotionLevel.OFF,
                    hapticsEnabled = false,
                    onVolumePreview = {},
                    onVolumeCommit = {},
                    onToggleMute = {},
                    onToggleLock = {},
                )
            }
        }
        composeRule
            .onNode(hasContentDescription("Volume fader for Mic/Aux, -1.9 dB, locked"))
            .assert(!hasSetProgress())
    }

    @Test
    fun lockedStripKeepsMuteVisibleAndAnnouncesLock() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                MixerStrip(
                    name = "Mic/Aux",
                    scope = MixerScope.GLOBAL,
                    volumeMul = 0.8,
                    muted = false,
                    locked = true,
                    meterHolder = MeterLevelsHolder(),
                    motionLevel = MotionLevel.OFF,
                    hapticsEnabled = false,
                    onVolumePreview = {},
                    onVolumeCommit = {},
                    onToggleMute = {},
                    onToggleLock = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription("Volume fader for Mic/Aux, -1.9 dB, locked")
            .assertExists()
        composeRule.onNodeWithContentDescription("Mute Mic/Aux").assertExists()
    }

    @Test
    fun sceneCardLabelsFollowTheTapAction() {
        assertEquals(
            "Scene Camera, ready, double-tap to switch",
            sceneCardDescription("Camera", SceneCardMode(active = false, preview = false)),
        )
        assertEquals(
            "Scene Camera, ready, double-tap to load in preview",
            sceneCardDescription(
                "Camera",
                SceneCardMode(active = false, preview = false, studioMode = true),
            ),
        )
        assertEquals(
            "Scene Camera, on preview, use Transition or Cut to take it to program",
            sceneCardDescription(
                "Camera",
                SceneCardMode(active = false, preview = true, studioMode = true),
            ),
        )
        assertEquals(
            "Scene Intro, on air, current program scene",
            sceneCardDescription("Intro", SceneCardMode(active = true, preview = false)),
        )
        assertEquals(
            "Scene Camera, ready, reorder mode, drag the grip to move",
            sceneCardDescription(
                "Camera",
                SceneCardMode(active = false, preview = false, reordering = true),
            ),
        )
        assertEquals(
            R.string.scene_drag_to_move,
            sceneCardCaption(SceneCardMode(active = false, preview = false, reordering = true)),
        )
        assertEquals(
            R.string.scene_tap_preview,
            sceneCardCaption(SceneCardMode(active = false, preview = false, studioMode = true)),
        )
    }

    @Test
    fun sceneCardInReorderModeAnnouncesReorder() {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                SceneCard(
                    label = "Camera",
                    icon = SceneDeckIcons.Scenes,
                    active = false,
                    reordering = true,
                )
            }
        }
        composeRule
            .onNodeWithContentDescription(
                "Scene Camera, ready, reorder mode, drag the grip to move"
            )
            .assertExists()
    }
}
