package com.scenedeck.android.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons

/** Navigation glyph for each destination, from the design-system icon set. */
val SceneDeckDestination.icon: ImageVector
    get() =
        when (this) {
            SceneDeckDestination.Live -> SceneDeckIcons.Scenes
            SceneDeckDestination.Mixer -> SceneDeckIcons.Mixer
            SceneDeckDestination.Stats -> SceneDeckIcons.Stats
            SceneDeckDestination.Inventory -> SceneDeckIcons.Inventory
            SceneDeckDestination.Graph -> SceneDeckIcons.Graph
            SceneDeckDestination.Doctor -> SceneDeckIcons.Doctor
            SceneDeckDestination.Settings -> SceneDeckIcons.Settings
            SceneDeckDestination.Connections -> SceneDeckIcons.Connections
            SceneDeckDestination.Onboarding -> SceneDeckIcons.Help
        }
