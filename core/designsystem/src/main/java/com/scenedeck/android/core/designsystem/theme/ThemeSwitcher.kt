package com.scenedeck.android.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.preview.DesignSystemPreview
import com.scenedeck.android.core.designsystem.theme.families.colorSchemeFor
import com.scenedeck.android.core.designsystem.theme.families.displayName

private val CheckIcon: ImageVector =
    ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        .apply {
            addPath(
                pathData = addPathNodes("M20,6 L9,17 L4,12"),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        .build()

/**
 * Vertical picker listing every [ThemeFamily] with a light + dark swatch preview per family and a
 * check on the selected entry. Used by the settings/app shell.
 */
@Composable
fun ThemeSwitcher(
    current: ThemeFamily,
    onSelect: (ThemeFamily) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeFamily.entries.forEach { family ->
            ThemeFamilyRow(
                family = family,
                selected = family == current,
                onClick = { onSelect(family) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThemeFamilyRow(
    family: ThemeFamily,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { this.selected = selected },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FamilySwatch(family = family, darkTheme = false)
            Spacer(Modifier.width(4.dp))
            FamilySwatch(family = family, darkTheme = true)
            Spacer(Modifier.width(16.dp))
            Text(
                text = family.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    imageVector = CheckIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Mini scheme preview: surface chip with primary/secondary/tertiary dots. */
@Composable
private fun FamilySwatch(family: ThemeFamily, darkTheme: Boolean, modifier: Modifier = Modifier) {
    val scheme = colorSchemeFor(family, darkTheme)
    val shape = RoundedCornerShape(6.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .background(scheme.surface)
                .padding(horizontal = 4.dp, vertical = 5.dp),
    ) {
        SwatchDot(scheme.primary)
        SwatchDot(scheme.secondary)
        SwatchDot(scheme.tertiary)
    }
}

@Composable
private fun SwatchDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@DesignSystemPreview
@Composable
private fun ThemeSwitcherPreview() {
    SceneDeckTheme {
        Surface {
            ThemeSwitcher(
                current = ThemeFamily.SCENEDECK,
                onSelect = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@DesignSystemPreview
@Composable
private fun ThemeSwitcherObsPreview() {
    SceneDeckTheme(family = ThemeFamily.OBS) {
        Surface {
            ThemeSwitcher(
                current = ThemeFamily.OBS,
                onSelect = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
