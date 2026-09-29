package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Inventory registry row (FEATURE_SPEC §6): scene icon, name, stale badge,
 * accent dot and role chip. All mutation happens through the callbacks; pickers
 * (icon/accent/role menu) are owned by the feature screen.
 */
@Composable
fun InventoryRow(
    name: String,
    icon: ImageVector,
    roleLabel: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    stale: Boolean = false,
    onIconClick: () -> Unit = {},
    onRoleClick: () -> Unit = {},
    onAccentClick: () -> Unit = {},
    onRemoveStale: () -> Unit = {},
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leadingContent?.invoke()

            // Icon (tap → icon picker).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        accentColor?.copy(alpha = 0.35f)
                            ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .clickable(onClick = onIconClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Change icon for $name",
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Name (+ stale badge).
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (stale) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = SceneDeckTheme.colors.warning.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = "stale",
                            style = MaterialTheme.typography.labelSmall,
                            color = SceneDeckTheme.colors.warning,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Accent dot (tap → accent picker).
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor ?: Color.Transparent)
                    .let { base ->
                        if (accentColor == null) {
                            base.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        } else {
                            base
                        }
                    }
                    .clickable(onClick = onAccentClick),
            )

            // Role chip (tap → role menu).
            Surface(
                onClick = onRoleClick,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            if (stale) {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onRemoveStale)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
