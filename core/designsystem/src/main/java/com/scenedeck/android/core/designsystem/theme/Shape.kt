package com.scenedeck.android.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Expressive shape scale (docs/DESIGN_SYSTEM.md §5): scene cards use [Shapes.large] (~28dp), mixer
 * strips [Shapes.medium], transport buttons [SceneDeckShapeTokens.Transport] (stadium/full).
 */
val SceneDeckShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(28.dp),
        largeIncreased = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(36.dp),
        extraLargeIncreased = RoundedCornerShape(44.dp),
        extraExtraLarge = RoundedCornerShape(52.dp),
    )

/** Named product shape tokens mapped onto the expressive scale. */
object SceneDeckShapeTokens {
    /** Scene cards: large expressive rounding. */
    val SceneCard = RoundedCornerShape(28.dp)

    /** Mixer strips: medium rounding. */
    val MixerStrip = RoundedCornerShape(16.dp)

    /** Transport buttons (stream/record): stadium. */
    val Transport = CircleShape
}
