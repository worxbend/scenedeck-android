package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.MutableWorkspaceContext
import com.scenedeck.android.core.data.NamedDeck
import com.scenedeck.android.core.data.NamedDeckCodec
import com.scenedeck.android.core.data.NamedDeckRepository
import com.scenedeck.android.core.data.NamedDeckSnapshot
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.data.previewImport
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import kotlinx.coroutines.runBlocking
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
class NamedDeckManagerRoborazziTest {
    @get:Rule val composeRule = createComposeRule()
    private val deck = NamedDeck("interview", "Interview", listOf("Camera", "Remote guest", "Wide"))
    private val available = listOf("Camera", "Wide", "Slides")
    private var draft: DeckEditorPage = DeckEditorPage.Browse

    private fun fixture(
        family: ThemeFamily,
        dark: Boolean,
        page: DeckEditorPage = DeckEditorPage.Browse,
    ) {
        composeRule.setContent {
            var editor by remember { mutableStateOf(DeckManagerState(TEST_WORKSPACE, page)) }
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    NamedDeckManagerContent(
                        editor,
                        NamedDeckSnapshot(listOf(deck)),
                        available,
                        onShow = {
                            editor = editor.copy(page = it)
                            draft = it
                        },
                        onSave = {},
                        onClose = {},
                        onImport = {},
                        onExport = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun capture(family: ThemeFamily, dark: Boolean, members: Boolean) {
        fixture(
            family,
            dark,
            if (members) DeckEditorPage.Members(deck, deck.sceneNames) else DeckEditorPage.Browse,
        )
        val page = if (members) "members" else "list"
        val theme = if (dark) "dark" else "light"
        composeRule
            .onRoot()
            .captureRoboImage("named_deck_${page}_${family.name.lowercase()}_$theme.png")
    }

    @Test fun listStudioDark() = capture(ThemeFamily.SCENEDECK, true, false)

    @Test fun listStudioLight() = capture(ThemeFamily.SCENEDECK, false, false)

    @Test fun listObsDark() = capture(ThemeFamily.OBS, true, false)

    @Test fun listObsLight() = capture(ThemeFamily.OBS, false, false)

    @Test fun membersStudioDark() = capture(ThemeFamily.SCENEDECK, true, true)

    @Test fun membersStudioLight() = capture(ThemeFamily.SCENEDECK, false, true)

    @Test fun membersObsDark() = capture(ThemeFamily.OBS, true, true)

    @Test fun membersObsLight() = capture(ThemeFamily.OBS, false, true)

    @Test
    fun memberEditorKeepsMissingScenesRemovableAndChangesOnlyDraftOrder() {
        fixture(ThemeFamily.SCENEDECK, true, DeckEditorPage.Members(deck, deck.sceneNames))
        composeRule.onNodeWithText("2 · Unavailable in OBS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Order for Wide").performClick()
        composeRule.onNodeWithText("Move up").performClick()
        assertEquals(
            listOf("Camera", "Wide", "Remote guest"),
            (draft as DeckEditorPage.Members).draft,
        )
        composeRule.onNodeWithText("Remote guest").performClick()
        assertEquals(listOf("Camera", "Wide"), (draft as DeckEditorPage.Members).draft)
        composeRule.onNodeWithText("Slides").performClick()
        assertEquals(listOf("Camera", "Wide", "Slides"), (draft as DeckEditorPage.Members).draft)
        assertEquals(listOf("Camera", "Remote guest", "Wide"), deck.sceneNames)
    }

    @Test
    fun filteringDisablesReorderAndCreateKeepsNameDraft() {
        fixture(ThemeFamily.SCENEDECK, true)
        composeRule.onNodeWithText("Create deck").performClick()
        composeRule.onNodeWithText("Deck name").performTextInput("Guests")
        assertEquals("Guests", (draft as DeckEditorPage.Name).draft)
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Interview").performClick()
        composeRule.onNodeWithText("Find scene to add or remove").performTextInput("Wide")
        composeRule.onNodeWithContentDescription("Order for Wide").assertIsNotEnabled()
    }

    private fun captureImport(family: ThemeFamily, dark: Boolean) {
        val page = runBlocking {
            val database =
                Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        SceneDeckDatabase::class.java,
                    )
                    .build()
            try {
                val repository = NamedDeckRepository(database, MutableWorkspaceContext())
                repository.create(TEST_WORKSPACE, "Interview", listOf("Camera"))
                val document =
                    NamedDeckCodec.encode(
                        NamedDeckSnapshot(
                            listOf(deck, NamedDeck("travel", "Travel", listOf("Slides")))
                        )
                    )
                DeckEditorPage.Import(repository.previewImport(TEST_WORKSPACE, document))
            } finally {
                database.close()
            }
        }
        fixture(family, dark, page)
        composeRule.onNodeWithText("1 new · 1 replaced").assertIsDisplayed()
        val theme = if (dark) "dark" else "light"
        composeRule
            .onRoot()
            .captureRoboImage("named_deck_import_${family.name.lowercase()}_$theme.png")
    }

    @Test fun importStudioDark() = captureImport(ThemeFamily.SCENEDECK, true)

    @Test fun importStudioLight() = captureImport(ThemeFamily.SCENEDECK, false)

    @Test fun importObsDark() = captureImport(ThemeFamily.OBS, true)

    @Test fun importObsLight() = captureImport(ThemeFamily.OBS, false)
}
