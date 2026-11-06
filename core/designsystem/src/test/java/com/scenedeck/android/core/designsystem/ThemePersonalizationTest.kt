package com.scenedeck.android.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.designsystem.theme.families.colorSchemeFor
import com.scenedeck.android.core.designsystem.theme.personalizeScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePersonalizationTest {
    @Test
    fun newPalettesAndAccentsKeepTextReadable() {
        val added =
            listOf(ThemeFamily.OCEAN, ThemeFamily.ROSEWOOD, ThemeFamily.AMBER, ThemeFamily.PAPER)
        for (family in added) for (dark in listOf(false, true)) for (accent in
            ThemeAccent.entries) {
            val scheme = personalizeScheme(colorSchemeFor(family, dark), dark, accent, true)
            readable(scheme.primary, scheme.onPrimary)
            readable(scheme.primaryContainer, scheme.onPrimaryContainer)
            readable(scheme.secondary, scheme.onSecondary)
            readable(scheme.secondaryContainer, scheme.onSecondaryContainer)
            readable(scheme.tertiary, scheme.onTertiary)
            readable(scheme.tertiaryContainer, scheme.onTertiaryContainer)
            readable(scheme.background, scheme.onBackground)
            readable(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
            readable(scheme.surfaceContainerHighest, scheme.primary)
        }
    }

    @Test
    fun customAccentsAreReadableAcrossExistingFamilies() {
        for (family in ThemeFamily.entries) for (dark in listOf(false, true)) for (accent in
            ThemeAccent.entries.filter { it != ThemeAccent.DEFAULT }) {
            val base = colorSchemeFor(family, dark)
            val changed =
                personalizeScheme(base, dark, accent, false, family == ThemeFamily.HIGH_CONTRAST)
            readable(changed.primary, changed.onPrimary)
            readable(changed.primaryContainer, changed.onPrimaryContainer)
            readable(changed.surfaceContainerHigh, changed.primary)
            assertEquals(base.error, changed.error)
            assertEquals(base.secondary, changed.secondary)
        }
    }

    @Test
    fun trueBlackOnlyChangesDarkSurfacesAndHighContrastRemainsExact() {
        val light = colorSchemeFor(ThemeFamily.OCEAN, false)
        assertEquals(
            light.background,
            personalizeScheme(light, false, ThemeAccent.DEFAULT, true).background,
        )
        val dark =
            personalizeScheme(colorSchemeFor(ThemeFamily.OCEAN, true), true, ThemeAccent.TEAL, true)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.surface)
        assertTrue(dark.surfaceContainerHigh != dark.background)
        val contrast = colorSchemeFor(ThemeFamily.HIGH_CONTRAST, true)
        assertSame(contrast, personalizeScheme(contrast, true, ThemeAccent.ROSE, true, true))
        assertEquals(ThemeAccent.DEFAULT, ThemeAccent.fromName("future"))
    }

    private fun readable(a: Color, b: Color) {
        val bright = maxOf(a.luminance(), b.luminance())
        val dim = minOf(a.luminance(), b.luminance())
        assertTrue(
            "Contrast ${(bright + 0.05f) / (dim + 0.05f)} for $a / $b",
            (bright + 0.05f) / (dim + 0.05f) >= 4.5f,
        )
    }
}
