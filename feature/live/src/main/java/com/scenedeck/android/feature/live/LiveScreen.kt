package com.scenedeck.android.feature.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme

/**
 * Live page (the deck) — hero screen. Grid of primary scene cards with tap-to-switch
 * and the stream/record transport always at hand (FEATURE_SPEC §2, milestone M3).
 *
 * [transport] is a slot so the app module can inject its TransportBar without this
 * module depending on :app.
 */
@Composable
fun LiveScreen(modifier: Modifier = Modifier, transport: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Live",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your scene deck: tap a primary scene to put it on program, with " +
                "stream and record controls always at hand.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        transport()
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = "Coming in M3",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The tactile scene grid — accent colors, glow-on-active " +
                        "cards and drag-to-reorder — arrives with the Live Deck milestone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LiveScreenPreview() {
    SceneDeckTheme {
        LiveScreen()
    }
}
