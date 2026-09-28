package com.scenedeck.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
import com.scenedeck.android.navigation.icon
import com.scenedeck.android.navigation.label
import com.scenedeck.android.ui.components.StatusStrip
import com.scenedeck.android.ui.components.TransportBar
import com.scenedeck.android.ui.components.mockStatusStripState

/**
 * App shell: app-owned Navigation 3 back stack inside an adaptive navigation suite
 * (bottom bar on phones, rail on expanded widths) with the persistent StatusStrip
 * docked above the navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneDeckApp(appState: SceneDeckAppState, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SceneDeckDestination.Live)
    val current = backStack.lastOrNull() as? SceneDeckDestination
    var moreSheetOpen by rememberSaveable { mutableStateOf(false) }

    fun selectTopLevel(destination: SceneDeckDestination) {
        if (current == destination) return
        // Tab-style navigation: Live stays the root, any other top-level
        // destination replaces the current one so Back always returns to Live.
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (destination != SceneDeckDestination.Live) backStack.add(destination)
    }

    val navDisplay: @Composable (Modifier) -> Unit = { displayModifier ->
        NavDisplay(
            backStack = backStack,
            modifier = displayModifier,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = sceneDeckEntryProvider(appState, backStack, ::selectTopLevel),
        )
    }

    if (current == SceneDeckDestination.Onboarding) {
        // Help/Onboarding is a pushed route: full-screen, no navigation suite.
        Surface(modifier = modifier.fillMaxSize()) {
            navDisplay(Modifier.fillMaxSize())
        }
    } else {
        NavigationSuiteScaffold(
            modifier = modifier,
            navigationSuiteItems = {
                SceneDeckDestination.primary.forEach { destination ->
                    item(
                        selected = current == destination,
                        onClick = { selectTopLevel(destination) },
                        icon = {
                            Icon(destination.icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) },
                    )
                }
                item(
                    selected = current in SceneDeckDestination.overflow,
                    onClick = { moreSheetOpen = true },
                    icon = { Icon(SceneDeckIcons.More, contentDescription = "More") },
                    label = { Text("More") },
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                navDisplay(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                // TODO(M3): feed StatusStrip from StatsRepository / ObsStateRepository.
                StatusStrip(state = mockStatusStripState)
            }
        }

        if (moreSheetOpen) {
            ModalBottomSheet(onDismissRequest = { moreSheetOpen = false }) {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                (SceneDeckDestination.overflow + SceneDeckDestination.Onboarding).forEach { destination ->
                    ListItem(
                        leadingContent = {
                            Icon(destination.icon, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            moreSheetOpen = false
                            if (destination == SceneDeckDestination.Onboarding) {
                                backStack.add(SceneDeckDestination.Onboarding)
                            } else {
                                selectTopLevel(destination)
                            }
                        },
                    ) {
                        Text(destination.label)
                    }
                }
            }
        }
    }
}

private fun sceneDeckEntryProvider(
    appState: SceneDeckAppState,
    backStack: NavBackStack<NavKey>,
    selectTopLevel: (SceneDeckDestination) -> Unit,
) = entryProvider<NavKey> {
    entry<SceneDeckDestination.Live> {
        LiveScreen(transport = { TransportBar(enabled = false) })
    }
    entry<SceneDeckDestination.Mixer> { MixerScreen() }
    entry<SceneDeckDestination.Stats> { StatsScreen() }
    entry<SceneDeckDestination.Inventory> { InventoryScreen() }
    entry<SceneDeckDestination.Graph> { GraphScreen() }
    entry<SceneDeckDestination.Doctor> { DoctorScreen() }
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
        )
    }
    entry<SceneDeckDestination.Connections> { ConnectionsScreen() }
    entry<SceneDeckDestination.Onboarding> {
        OnboardingScreen(
            onConnectClick = {
                backStack.removeLastOrNull()
                selectTopLevel(SceneDeckDestination.Connections)
            },
        )
    }
}
