package com.scenedeck.android.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Signature deck card (docs/DESIGN_SYSTEM.md §7): accent-tinted surface, icon +
 * label, Active = animated glow ring + container morph to the program tally color.
 * Press = spring scale ~0.96. Semantics: "Scene X, ready/active, double-tap to switch".
 *
 * Haptics on activation/press are the caller's job (settings-aware); motion physics
 * come from the theme's MotionScheme via the animation defaults.
 */
@Composable
fun SceneCard(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    pending: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val colors = SceneDeckTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "sceneCardPressScale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            active -> colors.program
            accentColor != null -> accentColor.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "sceneCardContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) colors.onProgram else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "sceneCardContent",
    )
    val glowWidth by animateDpAsState(
        targetValue = if (active) 3.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "sceneCardGlow",
    )
    val shape = MaterialTheme.shapes.large

    val stateDescription = if (active) {
        "active, current program scene"
    } else {
        "ready, double-tap to switch"
    }

    @OptIn(ExperimentalFoundationApi::class)
    Surface(
        modifier = modifier
            .semantics {
                contentDescription = "Scene $label, $stateDescription"
                role = Role.Button
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (glowWidth > 0.dp) {
                    Modifier.border(
                        BorderStroke(glowWidth, colors.program.copy(alpha = 0.85f)),
                        shape,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            }
            if (accentColor != null && !active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(accentColor, MaterialTheme.shapes.small),
                )
            }
        }
    }
}
