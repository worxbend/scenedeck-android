package com.scenedeck.android.core.designsystem

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.ThemePreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h350dp-mdpi")
class PersonalizedThemeRoborazziTest(private val family: ThemeFamily) {
    @get:Rule val compose = createComposeRule()

    @Test fun light() = render(false, ThemeAccent.DEFAULT, false)

    @Test fun dark() = render(true, ThemeAccent.DEFAULT, false)

    @Test fun trueBlackAccent() = render(true, ThemeAccent.VIOLET, true)

    private fun render(dark: Boolean, accent: ThemeAccent, black: Boolean) {
        compose.setContent { Surface { ThemePreview(family, dark, false, accent, black) } }
        val mode = if (dark) "dark" else "light"
        val surface = if (black) "black" else "tonal"
        compose
            .onRoot()
            .captureRoboImage("personalized_${family.name.lowercase()}_${mode}_$surface.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun families() =
            listOf(ThemeFamily.OCEAN, ThemeFamily.ROSEWOOD, ThemeFamily.AMBER, ThemeFamily.PAPER)
    }
}
