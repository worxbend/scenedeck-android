package com.scenedeck.android.feature.history

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.BookmarkTimes
import com.scenedeck.android.core.data.LocalBookmark
import com.scenedeck.android.core.data.TEST_WORKSPACE
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Dialog/text-field layout uses the established default-width workaround (Robolectric #8460).
@Config(sdk = [34], qualifiers = "h891dp-xhdpi")
class BookmarkLabelRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean) {
        compose.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    BookmarkLabelDialog(
                        LocalBookmark(
                            "mark",
                            TEST_WORKSPACE.key,
                            "observation",
                            0,
                            "Guest introduction",
                            BookmarkTimes(),
                        ),
                        { _, _ -> true },
                        {},
                    )
                }
            }
        }
        compose
            .onRoot()
            .captureRoboImage(
                "bookmark_label_${family.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            )
    }

    @Test fun studioDark() = capture(ThemeFamily.SCENEDECK, true)

    @Test fun studioLight() = capture(ThemeFamily.SCENEDECK, false)

    @Test fun obsDark() = capture(ThemeFamily.OBS, true)

    @Test fun obsLight() = capture(ThemeFamily.OBS, false)
}
