package com.scenedeck.android.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** User layout density (FEATURE_SPEC §9). Touch targets stay at least 48 dp in both modes. */
enum class UiDensity {
    /** Default spacing. */
    COMFORTABLE,

    /** Tighter gutters, gaps and row padding so more rows fit on a phone. */
    COMPACT;

    companion object {
        fun fromName(name: String?): UiDensity =
            entries.firstOrNull { it.name == name } ?: COMFORTABLE
    }
}

/** Shared layout spacing derived from [UiDensity] (docs/DESIGN_SYSTEM.md §5). */
@Immutable
data class SceneDeckSpacing(
    /** Horizontal page gutter for main and More screens. */
    val pageGutter: Dp,
    /** Gap between page sections (header → content, panel → panel). */
    val sectionGap: Dp,
    /** Gap between list rows and grid items. */
    val itemGap: Dp,
    /** Inner padding of list rows and panels. */
    val rowPadding: Dp,
)

/** Spacing for each density; comfortable matches the original fixed values. */
fun spacingFor(density: UiDensity): SceneDeckSpacing =
    when (density) {
        UiDensity.COMFORTABLE ->
            SceneDeckSpacing(
                pageGutter = 20.dp,
                sectionGap = 16.dp,
                itemGap = 8.dp,
                rowPadding = 12.dp,
            )
        UiDensity.COMPACT ->
            SceneDeckSpacing(
                pageGutter = 12.dp,
                sectionGap = 10.dp,
                itemGap = 4.dp,
                rowPadding = 8.dp,
            )
    }

/** Current [SceneDeckSpacing]; provided by [SceneDeckTheme]. */
val LocalSceneDeckSpacing = staticCompositionLocalOf { spacingFor(UiDensity.COMFORTABLE) }
