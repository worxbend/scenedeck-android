package com.scenedeck.android.feature.history

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.scenedeck.android.core.data.HistoryDetailState
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HistoryUiTest {
    @get:Rule val compose = createComposeRule()
    private val opened = mutableListOf<String?>()
    private val deleted = mutableListOf<String?>()
    private val exported = mutableListOf<String?>()
    private val settings = mutableListOf<Pair<Boolean, Int>>()
    private var bookmarks = 0

    private fun show(state: HistoryUiState = historyPreviewState()) {
        compose.setContent {
            SceneDeckTheme {
                Surface {
                    HistoryContent(
                        state,
                        { opened.add(it) },
                        { enabled, limit -> settings.add(enabled to limit) },
                        { deleted.add(it) },
                        { exported.add(it) },
                        {},
                        { bookmarks++ },
                    )
                }
            }
        }
    }

    @Test
    fun settingsAndRetentionUseExplicitControls() {
        show()
        compose.onNodeWithText("Capture history").performClick()
        assertEquals(listOf(false to 30), settings)
        compose.onNodeWithText("30 observations").performClick()
        compose.onNodeWithText("100 observations").performClick()
        assertEquals(true to 100, settings.last())
    }

    @Test
    fun clearNeedsConfirmationAndPreservesTheBookmarksEntryPoint() {
        show()
        compose.onNodeWithText("Clear history").performScrollTo().performClick()
        assertTrue(deleted.isEmpty())
        compose.onNodeWithText("Clear saved history").performClick()
        assertEquals(listOf<String?>(null), deleted)
        compose.onNodeWithText("Bookmarks").performScrollTo().performClick()
        assertEquals(1, bookmarks)
    }

    @Test
    fun observationActionsKeepTheSelectedIdentity() {
        val state = historyPreviewState(true)
        show(state)
        compose.onNodeWithText("Export observation").performScrollTo().performClick()
        assertEquals(listOf(state.selection.id), exported)
        compose.onNodeWithText("Delete observation").performScrollTo().performClick()
        assertTrue(deleted.isEmpty())
        compose.onNodeWithText("Delete permanently").performClick()
        assertEquals(listOf(state.selection.id), deleted)
        compose.onNodeWithText("All history").performScrollTo().performClick()
        assertEquals(listOf<String?>(null), opened)
    }

    @Test
    fun offlineListStillOpensAndExports() {
        val state = historyPreviewState()
        show(state)
        compose.onNodeWithText("Export history & bookmarks").performScrollTo().performClick()
        assertEquals(listOf<String?>(null), exported)
        compose.onNodeWithText("View observation").performScrollTo().performClick()
        assertEquals(listOf("preview"), opened)
    }

    @Test
    fun pendingWriteDisablesDuplicateMutations() {
        show(historyPreviewState().copy(operation = HistoryOperation(busy = true)))
        compose.onNodeWithText("Capture history").assertIsNotEnabled()
        compose.onNodeWithText("30 observations").assertIsNotEnabled()
        compose.onNodeWithText("Export history & bookmarks").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Clear history").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun missingObservationCannotExportAnotherSelection() {
        show(
            historyPreviewState(true)
                .copy(selection = HistorySelection("removed", HistoryDetailState.Missing))
        )
        compose.onNodeWithText("Export observation").assertDoesNotExist()
        compose.onNodeWithText("Delete observation").assertDoesNotExist()
        compose.onNodeWithText("All history").performClick()
        assertEquals(listOf<String?>(null), opened)
    }
}
