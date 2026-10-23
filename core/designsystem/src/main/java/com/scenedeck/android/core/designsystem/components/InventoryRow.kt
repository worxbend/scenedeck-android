package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Inventory registry row (FEATURE_SPEC §6): scene icon, name, stale badge, accent dot and role
 * chip. All mutation happens through the callbacks; pickers (icon/accent/role menu) are owned by
 * the feature screen.
 *
 * The name keeps the full flexible width (up to two lines); stale rows move the badge and the
 * Remove action to a second line so they never squeeze the scene name. [roleAssigned] = false
 * renders the role chip outlined, marking an implicit default instead of a chosen role.
 */
@Composable
fun InventoryRow(
    name: String,
    icon: ImageVector,
    roleLabel: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    stale: Boolean = false,
    roleAssigned: Boolean = true,
    onIconClick: () -> Unit = {},
    onRoleClick: () -> Unit = {},
    onAccentClick: () -> Unit = {},
    onRemoveStale: () -> Unit = {},
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val rowPadding = SceneDeckTheme.spacing.rowPadding
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = rowPadding - 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leadingContent?.invoke()
                SceneIconButton(name, icon, accentColor, onIconClick)
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AccentDot(name, accentColor, onAccentClick)
                RoleChip(name, roleLabel, roleAssigned, onRoleClick)
            }
            if (stale) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StaleBadge()
                    TextButton(
                        onClick = onRemoveStale,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Remove stale entry $name"
                            },
                    ) {
                        Text("Remove", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Icon well (tap → icon picker). */
@Composable
private fun SceneIconButton(
    name: String,
    icon: ImageVector,
    accentColor: Color?,
    onIconClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    accentColor?.copy(alpha = 0.35f)
                        ?: MaterialTheme.colorScheme.surfaceContainerHighest
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
}

/** Warning badge for registry entries whose scene is gone from OBS. */
@Composable
private fun StaleBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = SceneDeckTheme.colors.warning.copy(alpha = 0.2f),
    ) {
        Text(
            text = "stale · not in OBS",
            style = MaterialTheme.typography.labelSmall,
            color = SceneDeckTheme.colors.warning,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Accent dot (tap → accent picker). */
@Composable
private fun AccentDot(name: String, accentColor: Color?, onAccentClick: () -> Unit) {
    // 22dp dot inside a 48dp touch target.
    Box(
        modifier =
            Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClickLabel = "Change accent color for $name", onClick = onAccentClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp)
                .clip(CircleShape)
                .background(accentColor ?: MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

/** Role chip (tap → role menu); outlined when the role is only the implicit default. */
@Composable
private fun RoleChip(
    name: String,
    roleLabel: String,
    roleAssigned: Boolean,
    onRoleClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onRoleClick,
        shape = MaterialTheme.shapes.small,
        color = if (roleAssigned) scheme.primaryContainer else Color.Transparent,
        border = if (roleAssigned) null else BorderStroke(1.dp, scheme.outline),
        modifier =
            Modifier.semantics { contentDescription = "Role for $name: $roleLabel. Change role" },
    ) {
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (roleAssigned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
