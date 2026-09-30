package com.scenedeck.android

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.scenedeck.android.feature.connections.ConnectionsScreen
import com.scenedeck.android.feature.doctor.DoctorScreen
import com.scenedeck.android.feature.graph.GraphScreen
import com.scenedeck.android.feature.inventory.InventoryScreen
import com.scenedeck.android.feature.live.LiveScreen
import com.scenedeck.android.feature.mixer.MixerScreen
import com.scenedeck.android.feature.onboarding.OnboardingScreen
import com.scenedeck.android.feature.settings.SettingsScreen
import com.scenedeck.android.feature.stats.StatsScreen
import com.scenedeck.android.navigation.SceneDeckDestination

/** Nav3 entry provider wiring every destination to its feature screen. */
internal fun sceneDeckEntryProvider(
    appState: SceneDeckAppState,
    backStack: NavBackStack<NavKey>,
    selectTopLevel: (SceneDeckDestination) -> Unit,
) =
    entryProvider<NavKey> {
        liveEntry(selectTopLevel)
        mixerEntry(appState, selectTopLevel)
        statsEntry(selectTopLevel)
        inventoryEntry(selectTopLevel)
        graphEntry(selectTopLevel)
        doctorEntry(selectTopLevel)
        settingsEntry(appState)
        connectionsEntry()
        onboardingEntry(backStack)
    }

private fun EntryProviderScope<NavKey>.liveEntry(selectTopLevel: (SceneDeckDestination) -> Unit) {
    entry<SceneDeckDestination.Live> {
        LiveScreen(onNavigateToConnections = { selectTopLevel(SceneDeckDestination.Connections) })
    }
}

private fun EntryProviderScope<NavKey>.mixerEntry(
    appState: SceneDeckAppState,
    selectTopLevel: (SceneDeckDestination) -> Unit,
) {
    entry<SceneDeckDestination.Mixer> {
        MixerScreen(
            onNavigateToConnections = { selectTopLevel(SceneDeckDestination.Connections) },
            motionLevel = appState.motionLevel,
            hapticsEnabled = appState.haptics,
        )
    }
}

private fun EntryProviderScope<NavKey>.statsEntry(selectTopLevel: (SceneDeckDestination) -> Unit) {
    entry<SceneDeckDestination.Stats> {
        StatsScreen(onNavigateToConnections = { selectTopLevel(SceneDeckDestination.Connections) })
    }
}

private fun EntryProviderScope<NavKey>.inventoryEntry(
    selectTopLevel: (SceneDeckDestination) -> Unit
) {
    entry<SceneDeckDestination.Inventory> {
        InventoryScreen(
            onNavigateToConnections = { selectTopLevel(SceneDeckDestination.Connections) }
        )
    }
}

private fun EntryProviderScope<NavKey>.graphEntry(selectTopLevel: (SceneDeckDestination) -> Unit) {
    entry<SceneDeckDestination.Graph> {
        GraphScreen(onNavigateToConnections = { selectTopLevel(SceneDeckDestination.Connections) })
    }
}

private fun EntryProviderScope<NavKey>.doctorEntry(selectTopLevel: (SceneDeckDestination) -> Unit) {
    entry<SceneDeckDestination.Doctor> {
        DoctorScreen(
            onNavigateToConnections = { selectTopLevel(SceneDeckDestination.Connections) },
            onNavigateToInventory = { selectTopLevel(SceneDeckDestination.Inventory) },
        )
    }
}

private fun EntryProviderScope<NavKey>.settingsEntry(appState: SceneDeckAppState) {
    entry<SceneDeckDestination.Settings> {
        SettingsScreen(
            currentTheme = appState.themeFamily,
            onThemeSelect = { appState.themeFamily = it },
            darkMode = appState.darkMode,
            onDarkModeChange = { appState.darkMode = it },
            motionLevel = appState.motionLevel,
            onMotionLevelChange = { appState.motionLevel = it },
            dynamicColor = appState.dynamicColor,
            onDynamicColorChange = { appState.dynamicColor = it },
            haptics = appState.haptics,
            onHapticsChange = { appState.haptics = it },
            keepScreenOn = appState.keepScreenOn,
            onKeepScreenOnChange = { appState.keepScreenOn = it },
        )
    }
}

private fun EntryProviderScope<NavKey>.connectionsEntry() {
    entry<SceneDeckDestination.Connections> { ConnectionsScreen() }
}

private fun EntryProviderScope<NavKey>.onboardingEntry(backStack: NavBackStack<NavKey>) {
    entry<SceneDeckDestination.Onboarding> {
        OnboardingScreen(
            onFinished = { backStack.removeLastOrNull() },
            onSkip = { backStack.removeLastOrNull() },
        )
    }
}
