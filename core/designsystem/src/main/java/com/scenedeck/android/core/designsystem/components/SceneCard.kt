package com.scenedeck.android.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.R
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

private val CardRadius = 24.dp
private val IconChipSize = 36.dp

/**
 * Studio deck tile: uniform card grid, softly tinted surfaces, one icon and readable scene/status
 * labels. Thumbnails retain a dark scrim; placeholder tiles use theme-aware foregrounds. Program
 * and preview retain semantic tally colors.
 */
@Composable
fun SceneCard(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    pending: Boolean = false,
    /** Studio-mode preview state (green, PRODUCT LAW per SceneDeckColors.preview). */
    preview: Boolean = false,
    /** Live scene thumbnail (GetSourceScreenshot); rich placeholder when null. */
    thumbnail: ImageBitmap? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    sceneNumber: Int? = null,
    motionLevel: MotionLevel = MotionLevel.FULL,
    studioMode: Boolean = false,
) {
    val colors = SceneDeckTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by
        animateFloatAsState(
            targetValue = if (pressed && motionLevel == MotionLevel.FULL) 0.97f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "sceneCardPressScale",
        )
    val stateColor by
        animateColorAsState(
            targetValue = sceneStateColor(active, preview, accentColor),
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "sceneCardStateColor",
        )
    val shape = RoundedCornerShape(CardRadius)
    val minimumHeight = 148.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)

    val stateDescription =
        when {
            active -> "on air, current program scene"
            preview -> "on preview, double-tap to transition"
            else -> "ready, double-tap to switch"
        }

    @OptIn(ExperimentalFoundationApi::class)
    Surface(
        modifier =
            modifier
                .semantics {
                    contentDescription = "Scene $label, $stateDescription"
                    role = Role.Button
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                // Soft outer glow while on air / on preview (tinted shadow, no hard ring).
                .then(
                    if (active || preview) {
                        Modifier.shadow(
                            elevation = 4.dp,
                            shape = shape,
                            ambientColor = stateColor,
                            spotColor = stateColor,
                        )
                    } else {
                        Modifier
                    }
                ),
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(minimumHeight)
                    .clip(shape)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
        ) {
            SceneCardArtwork(thumbnail, active || preview, stateColor)
            SceneCardLabels(
                label,
                icon,
                active,
                preview,
                pending,
                studioMode,
                thumbnail != null,
                sceneNumber,
                stateColor,
            )
            SceneCardBorder(active || preview, stateColor, shape)
            if (pending && motionLevel == MotionLevel.FULL) ShimmerSweep(shape)
        }
    }
}

@Composable
private fun SceneCardBorder(highlighted: Boolean, stateColor: Color, shape: RoundedCornerShape) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .border(
                    BorderStroke(
                        width = if (highlighted) 2.dp else 1.dp,
                        color =
                            if (highlighted) {
                                stateColor.copy(alpha = 0.9f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            },
                    ),
                    shape,
                )
    )
}

/** Soft diagonal tonal wash; scene identity comes from the icon, never a side rail. */
@Composable
private fun CardPlaceholder(accent: Color) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(lerp(surface, accent, 0.16f), lerp(surface, accent, 0.025f)),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                )
    )
}

/** Tonal icon chip (top-start). */
@Composable
private fun IconChip(icon: ImageVector, accent: Color, foreground: Color) {
    Box(
        modifier =
            Modifier.size(IconChipSize)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (foreground == Color.White) foreground else accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Tally state pill (LIVE / PREVIEW). */
@Composable
private fun StatePill(text: String, color: Color, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Diagonal shimmer sweep for the pending (switching) state. */
@Composable
private fun ShimmerSweep(shape: RoundedCornerShape) {
    val sweep =
        androidx.compose.animation.core
            .rememberInfiniteTransition(label = "cardShimmer")
            .animateFloat(
                initialValue = -0.2f,
                targetValue = 1.2f,
                animationSpec =
                    androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(900)
                    ),
                label = "cardShimmerOffset",
            )
    val mid = sweep.value.coerceIn(0f, 1f)
    val lo = (mid - 0.18f).coerceIn(0f, mid)
    val hi = (mid + 0.18f).coerceIn(mid, 1f)
    Box(
        modifier =
            Modifier.fillMaxSize()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        lo to Color.Transparent,
                        mid to Color.White.copy(alpha = 0.10f),
                        hi to Color.Transparent,
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                )
    )
}

@Composable
private fun SceneCardArtwork(thumbnail: ImageBitmap?, highlighted: Boolean, stateColor: Color) {
    // Layer 1: thumbnail or accent-gradient placeholder with a large glyph.
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (highlighted) 0.9f else 0.7f,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        CardPlaceholder(accent = stateColor)
    }
    // Layer 2: bottom scrim for label readability.
    if (thumbnail != null)
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        )
                    )
        )
}

@Composable
private fun SceneCardLabels(
    label: String,
    icon: ImageVector,
    active: Boolean,
    preview: Boolean,
    pending: Boolean,
    studioMode: Boolean,
    hasThumbnail: Boolean,
    sceneNumber: Int?,
    stateColor: Color,
) {
    // One icon and a clear label/state hierarchy keep the grid calm and scannable.
    val foreground = if (hasThumbnail) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = icon, accent = stateColor, foreground = foreground)
            Spacer(Modifier.weight(1f))
            sceneNumber?.let { number ->
                Text(
                    number.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground.copy(alpha = 0.55f),
                )
            }
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            SceneCardCaption(active, preview, pending, studioMode, hasThumbnail, stateColor)
        }
    }
}

@Composable
private fun SceneCardCaption(
    active: Boolean,
    preview: Boolean,
    pending: Boolean,
    studioMode: Boolean,
    hasThumbnail: Boolean,
    stateColor: Color,
) {
    val colors = SceneDeckTheme.colors
    if (active || preview) {
        StatePill(
            text = stringResource(if (active) R.string.scene_program else R.string.scene_preview),
            color = stateColor,
            contentColor = if (active) colors.onProgram else colors.onPreview,
        )
    } else {
        Text(
            text =
                stringResource(
                    when {
                        pending -> R.string.scene_switching
                        studioMode -> R.string.scene_tap_preview
                        else -> R.string.scene_tap_switch
                    }
                ),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (hasThumbnail) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun sceneStateColor(active: Boolean, preview: Boolean, accentColor: Color?): Color =
    when {
        active -> SceneDeckTheme.colors.program
        preview -> SceneDeckTheme.colors.preview
        else -> accentColor ?: MaterialTheme.colorScheme.primary
    }
