package com.scenedeck.android.feature.mixer

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.MediaPlayback
import com.scenedeck.android.core.data.MediaSeekResult
import com.scenedeck.android.core.data.MediaTarget
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.MediaStateKind
import com.scenedeck.android.core.model.MediaStatus
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
class MediaToolsRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val known =
        MediaPlayback(
            MediaTarget(TEST_WORKSPACE, "Intro clip", 1),
            MediaStatus(MediaStateKind.PAUSED, 90_000, 12_345),
            0,
        )
    private val seeks = mutableListOf<Long>()

    private fun show(
        playback: MediaPlayback? = known,
        supported: Boolean = true,
        locked: Boolean = false,
        family: ThemeFamily = ThemeFamily.SCENEDECK,
        dark: Boolean = true,
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    MediaToolsContent(
                        "Intro clip",
                        playback,
                        supported,
                        locked,
                        { seeks.add(it.cursorMs) },
                        {},
                    )
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean) {
        show(family = family, dark = dark)
        compose
            .onRoot()
            .captureRoboImage(
                "media_tools_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun accessibleSeekCommitsOnceAndDoesNotInventConfirmedProgress() {
        show()
        compose.onNodeWithContentDescription("Seek position for Intro clip").performSemanticsAction(
            SemanticsActions.SetProgress
        ) {
            it(0.5f)
        }
        assertEquals(listOf(45_000L), seeks)
        compose.onNodeWithText("Elapsed · 00:00:12").assertIsDisplayed()
        compose.onNodeWithText("Remaining · 00:01:17").assertIsDisplayed()
    }

    @Test
    fun dragSendsOneSeekOnlyAfterRelease() {
        show()
        val slider = compose.onNodeWithContentDescription("Seek position for Intro clip")
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * 0.65f, centerY))
        }
        assertEquals(0, seeks.size)
        slider.performTouchInput {
            moveTo(Offset(width * 0.75f, centerY))
            up()
        }
        assertEquals(1, seeks.size)
    }

    @Test
    fun sourceReplacementDuringDragDiscardsTheWholeGesture() {
        val playback = mutableStateOf(known)
        compose.setContent {
            SceneDeckTheme {
                Surface {
                    MediaToolsContent(
                        "Intro clip",
                        playback.value,
                        true,
                        false,
                        { seeks.add(it.cursorMs) },
                        {},
                    )
                }
            }
        }
        val slider = compose.onNodeWithContentDescription("Seek position for Intro clip")
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * 0.65f, centerY))
        }
        compose.runOnIdle { playback.value = known.copy(target = known.target.copy(revision = 2)) }
        slider.performTouchInput {
            moveTo(Offset(width * 0.75f, centerY))
            up()
        }
        assertEquals(0, seeks.size)
        compose
            .onNodeWithText("Media changed during the gesture. Adjust the position again.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun unknownDurationKeepsElapsedButDisablesSeek() {
        show(known.copy(status = known.status!!.copy(durationMs = null)))
        compose.onNodeWithText("Elapsed · 00:00:12").assertIsDisplayed()
        compose.onNodeWithText("Remaining · Unknown").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seek position for Intro clip").assertIsNotEnabled()
    }

    @Test
    fun lockedChannelAllowsReadingButNotSeeking() {
        show(locked = true)
        compose.onNodeWithText("Elapsed · 00:00:12").assertIsDisplayed()
        compose.onNodeWithText("Unlock this channel to seek.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seek position for Intro clip").assertIsNotEnabled()
    }

    @Test
    fun unsupportedRequestExplainsUnavailableSeek() {
        show(supported = false)
        compose.onNodeWithText("Seeking is unavailable on this OBS connection.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seek position for Intro clip").assertIsNotEnabled()
    }

    @Test
    fun pendingSeekDisablesDuplicateAndShowsWaiting() {
        show(known.copy(seeking = true))
        compose.onNodeWithContentDescription("Seek position for Intro clip").assertIsNotEnabled()
        compose.onNodeWithText("Seeking… Waiting for OBS.").assertIsDisplayed()
        compose.onRoot().captureRoboImage("media_tools_pending.png")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun compactUnknownOutcomeKeepsExplanationAndCloseReachable() {
        show(
            known.copy(status = null, receivedAtMs = null, result = MediaSeekResult.UNKNOWN),
            dark = false,
        )
        compose
            .onNodeWithContentDescription("Seek position for Intro clip")
            .performScrollTo()
            .assertIsNotEnabled()
        compose
            .onNodeWithText(
                "Outcome unknown. Check OBS before trying again; the seek was not retried."
            )
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("media_tools_compact_unknown.png")
    }
}
