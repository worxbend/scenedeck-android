package com.scenedeck.android.feature.history

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.BookmarkOutputState
import com.scenedeck.android.core.data.BookmarkOutputTime
import com.scenedeck.android.core.data.BookmarkTimes
import com.scenedeck.android.core.data.BookmarksState
import com.scenedeck.android.core.data.LocalBookmark
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.WorkspaceState
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
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
class BookmarksRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val originalZone = TimeZone.getDefault()
    private val originalLocale = Locale.getDefault()
    private val bookmark =
        LocalBookmark(
            "mark",
            TEST_WORKSPACE.key,
            "observation",
            1_793_843_000_000,
            "Guest introduction",
            BookmarkTimes(
                BookmarkOutputTime(BookmarkOutputState.ACTIVE, 90_005, 500),
                BookmarkOutputTime(BookmarkOutputState.PAUSED, 12_345, 1_000),
            ),
        )
    private val ready =
        BookmarksUiState(
            BookmarksState.Ready(listOf(bookmark)),
            TEST_WORKSPACE,
            mapOf(7L to "Studio rig"),
        )
    private var marked = 0
    private var retried = 0
    private var discarded = 0
    private var deleted = 0
    private var edited: String? = null

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    private fun show(
        state: BookmarksUiState = ready,
        family: ThemeFamily = ThemeFamily.SCENEDECK,
        dark: Boolean = true,
        save: Boolean = true,
    ) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    BookmarksContent(
                        state,
                        { marked++ },
                        { retried++ },
                        { discarded++ },
                        { retried++ },
                        { _, label ->
                            edited = label
                            save
                        },
                        { deleted++ },
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
                "bookmarks_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)

    @Test
    fun captureAndDeleteAreExplicitSeparateActions() {
        show()
        compose.onNodeWithText("Mark now").performClick()
        assertEquals(1, marked)
        compose
            .onNodeWithText("Recording · 00:00:12.345 · paused")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        assertEquals(0, deleted)
        compose.onNodeWithText("Delete bookmark").performClick()
        assertEquals(1, deleted)
    }

    @Test
    @Config(
        qualifiers = "h891dp-xhdpi"
    ) // Same dialog-width workaround as SceneAliasDialogRoborazziTest.
    fun savedBookmarksStayReadableAndEditableOffline() {
        show(ready.copy(workspace = null), save = false)
        compose.onNodeWithText("Mark now").assertIsNotEnabled()
        compose.onNodeWithText("Edit label").performScrollTo().performClick()
        compose.onNodeWithText("Label").performTextReplacement("New label")
        compose.onNodeWithText("Save").performClick()
        compose
            .onNodeWithText(
                "Couldn't save. The bookmark may have changed; your draft is kept here."
            )
            .assertIsDisplayed()
        compose.onNodeWithText("New label").assertIsDisplayed()
        assertEquals("New label", edited)
    }

    @Test
    fun failedCaptureOffersRetryAndDiscardAndBlocksRecapture() {
        show(
            ready.copy(
                operation =
                    BookmarkOperation(unsaved = bookmark, message = "Couldn't save this moment.")
            )
        )
        compose.onNodeWithText("Mark now").assertIsNotEnabled()
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Discard unsaved bookmark").performClick()
        assertEquals(1, retried)
        assertEquals(1, discarded)
    }

    @Test
    fun failedWorkspaceAndStorageRemainUnknownWithIndependentRetryActions() {
        show(
            ready.copy(
                workspace = null,
                workspaceState = WorkspaceState.Failed,
                saved = BookmarksState.Failed,
            )
        )
        compose.onNodeWithText("Mark now").assertIsNotEnabled()
        compose.onNodeWithText("Retry workspace").performClick()
        compose.onNodeWithText("Retry loading").performScrollTo().performClick()
        assertEquals(2, retried)
        compose
            .onNodeWithText("No bookmarks yet. Mark a moment while connected to OBS.")
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun compactHeightKeepsUnknownReadingsAndActionsReachable() {
        show(
            ready.copy(
                workspace = null,
                saved = BookmarksState.Ready(listOf(bookmark.copy(times = BookmarkTimes()))),
            ),
            dark = false,
        )
        compose
            .onNodeWithText("Recording · unknown at capture")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Edit label").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("bookmarks_compact_unknown.png")
    }
}
