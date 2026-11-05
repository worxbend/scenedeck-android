package com.scenedeck.android.feature.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.MixerInputState
import com.scenedeck.android.core.designsystem.components.MeterLevelsStore
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.MixerScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1400dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OperatorMixerTest {
    @get:Rule val compose = createComposeRule()
    private var width by mutableStateOf(1200.dp)
    private var height by mutableStateOf(800.dp)
    private var search by mutableStateOf("")
    private var muted: String? = null
    private var media: String? = null
    private val levels = MeterLevelsStore()

    @Test fun sceneDeckDark() = render(ThemeFamily.SCENEDECK, true)

    @Test fun sceneDeckLight() = render(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = render(ThemeFamily.OBS, true)

    @Test fun obsLight() = render(ThemeFamily.OBS, false)

    @Test
    fun portrait() {
        width = 900.dp
        height = 1100.dp
        render(ThemeFamily.SCENEDECK, false, suffix = "portrait")
    }

    @Test
    fun compactLargeText() {
        width = 760.dp
        height = 340.dp
        render(ThemeFamily.SCENEDECK, true, 1.3f, "compact")
    }

    @Test
    fun largeText() {
        width = 960.dp
        height = 600.dp
        render(ThemeFamily.SCENEDECK, false, 2f, "large_text")
    }

    @Test
    fun resizeRetainsSearchFocusAndChannelActionsKeepTargets() {
        render(ThemeFamily.SCENEDECK, true, suffix = "resize_before")
        compose.onNodeWithContentDescription("Mute Microphone").performClick()
        assertEquals("Microphone", muted)
        compose.onNodeWithContentDescription("Channel options for Microphone").performClick()
        compose.onNodeWithText("Media tools").performClick()
        assertEquals("Microphone", media)
        compose.onNodeWithText("Find a channel").performTextInput("mic")
        compose.runOnIdle { width = 393.dp }
        compose.onNodeWithText("mic").assertIsDisplayed().assertIsFocused()
        compose.runOnIdle { width = 1200.dp }
        compose.onNodeWithText("mic").assertIsDisplayed().assertIsFocused()
    }

    private fun render(
        family: ThemeFamily,
        dark: Boolean,
        fontScale: Float = 1f,
        suffix: String = "${family.name.lowercase()}_${if (dark) "dark" else "light"}",
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    Box(Modifier.requiredSize(width, height).testTag("operator")) {
                        Surface {
                            MixerContent(
                                uiState =
                                    MixerUiState(
                                        displayedScene = "Interview",
                                        search = search,
                                        inputs =
                                            listOf(
                                                    "Microphone",
                                                    "Desktop audio",
                                                    "Music",
                                                    "Guest microphone",
                                                    "Remote guest",
                                                    "Opening clip",
                                                )
                                                .mapIndexed { i, name ->
                                                    MixerInputState(
                                                        name,
                                                        MixerScope.GLOBAL,
                                                        null,
                                                        0.8,
                                                        false,
                                                        i == 2,
                                                        inputKind = "ffmpeg_source",
                                                    )
                                                },
                                    ),
                                levelsStore = levels,
                                motionLevel = MotionLevel.OFF,
                                hapticsEnabled = false,
                                callbacks =
                                    MixerCallbacks(
                                        onSearchChange = { search = it },
                                        onToggleMute = { name, _ -> muted = name },
                                    ),
                                onMediaClick = { media = it },
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Audio mixer").assertIsDisplayed()
        compose.onNodeWithTag("operator").captureRoboImage("operator_mixer_$suffix.png")
    }
}
