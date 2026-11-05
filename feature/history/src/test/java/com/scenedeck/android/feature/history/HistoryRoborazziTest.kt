package com.scenedeck.android.feature.history

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.HistoryCaptureFailure
import com.scenedeck.android.core.data.HistoryCapturePhase
import com.scenedeck.android.core.data.HistoryCaptureState
import com.scenedeck.android.core.data.HistoryState
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HistoryRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val zone = TimeZone.getDefault()
    private val locale = Locale.getDefault()

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        Locale.setDefault(locale)
        TimeZone.setDefault(zone)
    }

    private fun show(state: HistoryUiState, family: ThemeFamily, dark: Boolean) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    HistoryContent(state, {}, { _, _ -> }, {}, {}, {}, {})
                }
            }
        }
    }

    private fun capture(detail: Boolean, family: ThemeFamily, dark: Boolean) {
        show(historyPreviewState(detail), family, dark)
        val page = if (detail) "detail" else "list"
        val mode = if (dark) "dark" else "light"
        compose.onRoot().captureRoboImage("history_${page}_${family.name.lowercase()}_$mode.png")
    }

    @Test fun studioListDark() = capture(false, ThemeFamily.SCENEDECK, true)

    @Test fun studioListLight() = capture(false, ThemeFamily.SCENEDECK, false)

    @Test fun obsListDark() = capture(false, ThemeFamily.OBS, true)

    @Test fun obsListLight() = capture(false, ThemeFamily.OBS, false)

    @Test fun studioDetailDark() = capture(true, ThemeFamily.SCENEDECK, true)

    @Test fun studioDetailLight() = capture(true, ThemeFamily.SCENEDECK, false)

    @Test fun obsDetailDark() = capture(true, ThemeFamily.OBS, true)

    @Test fun obsDetailLight() = capture(true, ThemeFamily.OBS, false)

    private fun capturePreset(family: ThemeFamily, dark: Boolean, name: String) {
        show(historyPreviewState(true), family, dark)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Preset · Interview start"))
        compose.onNodeWithText("Preset · Interview start").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2. Start recording · unknown").assertIsDisplayed()
        compose.onRoot().captureRoboImage("history_preset_$name.png")
    }

    @Test fun presetStudioDark() = capturePreset(ThemeFamily.SCENEDECK, true, "studio_dark")

    @Test fun presetStudioLight() = capturePreset(ThemeFamily.SCENEDECK, false, "studio_light")

    @Test fun presetObsDark() = capturePreset(ThemeFamily.OBS, true, "obs_dark")

    @Test fun presetObsLight() = capturePreset(ThemeFamily.OBS, false, "obs_light")

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun compactPresetKeepsUnknownResultAndRemainingStepsReadable() {
        show(historyPreviewState(true), ThemeFamily.SCENEDECK, false)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Preset · Interview start"))
        compose.onNodeWithText("2. Start recording · unknown").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("3. Program: Guest camera · not run")
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "An unknown write may have taken effect. Check OBS before running again."
            )
            .performScrollTo()
            .assertIsDisplayed()
        compose.onRoot().captureRoboImage("history_preset_compact.png")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun compactListKeepsObservationControlsReachable() {
        show(historyPreviewState(), ThemeFamily.SCENEDECK, false)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("View observation"))
        compose.onNodeWithText("View observation").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("history_compact_list.png")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun compactTimelineKeepsLinkedBookmarkReadable() {
        show(historyPreviewState(true), ThemeFamily.SCENEDECK, false)
        compose
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Bookmark · Guest introduction"))
        compose
            .onNodeWithText("Bookmark · Guest introduction")
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText("Recording · 00:00:50.000 · paused")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onRoot().captureRoboImage("history_compact_bookmark.png")
    }

    @Test
    fun storageFailureShowsRetryWithoutAnEmptySuccessMessage() {
        show(
            historyPreviewState()
                .copy(
                    saved = HistoryState.Failed,
                    capture =
                        HistoryCaptureState(
                            HistoryCapturePhase.FAILED,
                            failure = HistoryCaptureFailure.STORAGE,
                        ),
                ),
            ThemeFamily.OBS,
            true,
        )
        compose.onNodeWithText("Retry capture").assertIsDisplayed()
        compose.onNodeWithText("Retry loading").assertIsDisplayed()
        compose.onRoot().captureRoboImage("history_storage_failure.png")
    }
}
