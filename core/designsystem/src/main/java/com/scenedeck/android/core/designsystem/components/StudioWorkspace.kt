package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/** Stable slots keep dialogs/focus/scroll owners when available window bounds change. */
@Composable
fun StudioWorkspace(
    modifier: Modifier = Modifier,
    controls: @Composable (expanded: Boolean) -> Unit,
    content: @Composable (expanded: Boolean) -> Unit,
) {
    val gap = SceneDeckTheme.spacing.sectionGap
    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp && maxHeight >= 480.dp
        val beside = expanded || (maxWidth >= 600.dp && maxHeight < 480.dp)
        Layout(
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) { controls(expanded) }
                Box { content(expanded) }
            },
            modifier = Modifier.fillMaxSize(),
        ) { slots, bounds ->
            if (beside) beside(slots, bounds, gap.roundToPx()) else stacked(slots, bounds)
        }
    }
}

private fun MeasureScope.beside(
    slots: List<Measurable>,
    bounds: Constraints,
    gap: Int,
): androidx.compose.ui.layout.MeasureResult {
    val width = bounds.maxWidth
    val height = bounds.maxHeight
    val controlWidth = (width * 0.42f).toInt().coerceIn(320.dp.roundToPx(), 400.dp.roundToPx())
    val control =
        slots[0].measure(
            Constraints(minWidth = controlWidth, maxWidth = controlWidth, maxHeight = height)
        )
    val body = slots[1].measure(Constraints.fixed(width - controlWidth - gap, height))
    return layout(width, height) {
        control.placeRelative(0, 0)
        body.placeRelative(controlWidth + gap, 0)
    }
}

private fun MeasureScope.stacked(
    slots: List<Measurable>,
    bounds: Constraints,
): androidx.compose.ui.layout.MeasureResult {
    val width = bounds.maxWidth
    val height = bounds.maxHeight
    val control =
        slots[0].measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = (height * 0.55f).toInt())
        )
    val body = slots[1].measure(Constraints.fixed(width, height - control.height))
    return layout(width, height) {
        control.placeRelative(0, 0)
        body.placeRelative(0, control.height)
    }
}
