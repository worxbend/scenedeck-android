package com.scenedeck.android.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe routes for the app-owned Navigation 3 back stack.
 *
 * [primary] destinations get dedicated tabs in the navigation suite; [overflow]
 * destinations are reachable from the "More" sheet. [Onboarding] doubles as the
 * Help route and is pushed on top of the stack (full-screen, no nav suite).
 */
@Serializable
sealed interface SceneDeckDestination : NavKey {

    @Serializable
    data object Live : SceneDeckDestination

    @Serializable
    data object Mixer : SceneDeckDestination

    @Serializable
    data object Stats : SceneDeckDestination

    @Serializable
    data object Inventory : SceneDeckDestination

    @Serializable
    data object Graph : SceneDeckDestination

    @Serializable
    data object Doctor : SceneDeckDestination

    @Serializable
    data object Settings : SceneDeckDestination

    @Serializable
    data object Connections : SceneDeckDestination

    @Serializable
    data object Onboarding : SceneDeckDestination

    companion object {
        val primary: List<SceneDeckDestination> = listOf(Live, Mixer, Stats, Inventory)
        val overflow: List<SceneDeckDestination> = listOf(Graph, Doctor, Connections, Settings)
        val topLevel: List<SceneDeckDestination> = primary + overflow
    }
}

/** User-facing label for navigation chrome. */
val SceneDeckDestination.label: String
    get() = when (this) {
        SceneDeckDestination.Live -> "Live"
        SceneDeckDestination.Mixer -> "Mixer"
        SceneDeckDestination.Stats -> "Stats"
        SceneDeckDestination.Inventory -> "Inventory"
        SceneDeckDestination.Graph -> "Graph"
        SceneDeckDestination.Doctor -> "Doctor"
        SceneDeckDestination.Settings -> "Settings"
        SceneDeckDestination.Connections -> "Connections"
        SceneDeckDestination.Onboarding -> "Help"
    }
