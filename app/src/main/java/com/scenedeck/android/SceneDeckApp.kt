package com.scenedeck.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioSectionHeader
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.feature.onboarding.OnboardingScreen
import com.scenedeck.android.navigation.SceneDeckDestination
import com.scenedeck.android.navigation.icon
import com.scenedeck.android.navigation.label
import com.scenedeck.android.ui.components.BackgroundSettingsSheet
import com.scenedeck.android.ui.components.KeepScreenOnEffect
import com.scenedeck.android.ui.components.StatusStrip
import com.scenedeck.android.ui.components.StatusStripState

/**
 * App shell. First run ([onboardingCompleted] false) shows the wizard full-screen; once completed
 * (persisted), the adaptive navigation suite hosts the Nav3 back stack with the persistent
 * StatusStrip docked above the navigation bar.
 */
@Composable
fun SceneDeckApp(
    appState: SceneDeckAppState,
    connectionState: ConnectionState,
    stripState: StatusStripState,
    onboardingCompleted: Boolean?,
    skipToConnections: Boolean,
    onOnboardingSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (onboardingCompleted) {
        null ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        false ->
            Surface(modifier = modifier.fillMaxSize()) {
                OnboardingScreen(
                    onFinished = {},
                    onSkip = onOnboardingSkip,
                )
            }

        true ->
            SceneDeckShell(
                appState = appState,
                connectionState = connectionState,
                stripState = stripState,
                startWithConnections = skipToConnections,
                modifier = modifier,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneDeckShell(
    appState: SceneDeckAppState,
    connectionState: ConnectionState,
    stripState: StatusStripState,
    startWithConnections: Boolean,
    modifier: Modifier = Modifier,
) {
    KeepScreenOnEffect(enabled = appState.keepScreenOn && connectionState is ConnectionState.Ready)

    val backStack = rememberNavBackStack(SceneDeckDestination.Live)
    LaunchedEffect(startWithConnections) {
        if (startWithConnections) backStack.selectTopLevel(SceneDeckDestination.Connections)
    }
    val current = backStack.lastOrNull() as? SceneDeckDestination
    // Onboarding/Help is a pushed route: full-screen, navigation-suite chrome hidden.
    val fullScreen = current == SceneDeckDestination.Onboarding
    var moreSheetOpen by rememberSaveable { mutableStateOf(false) }
    var backgroundSheetOpen by rememberSaveable { mutableStateOf(false) }

    val selectTopLevel: (SceneDeckDestination) -> Unit = { destination ->
        backStack.selectTopLevel(destination)
    }

    // Single NavDisplay call site: pushing routes never removes it from composition, so
    // per-entry remember/rememberSaveable state and ViewModelStores survive.
    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType =
            if (fullScreen) {
                NavigationSuiteType.None
            } else {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfo()
                )
            },
        navigationSuiteItems = {
            SceneDeckDestination.primary.forEach { destination ->
                item(
                    selected = current == destination,
                    onClick = { selectTopLevel(destination) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
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
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onBack = { backStack.removeLastOrNull() },
                entryProvider = sceneDeckEntryProvider(appState, backStack, selectTopLevel),
            )
            if (!fullScreen) {
                StatusStrip(
                    state = stripState,
                    onConnectionClick = { selectTopLevel(SceneDeckDestination.Connections) },
                    onConnectionLongClick = { backgroundSheetOpen = true },
                    animateConnection = appState.motionLevel == MotionLevel.FULL,
                )
            }
        }
    }

    if (backgroundSheetOpen) {
        BackgroundSettingsSheet(onDismiss = { backgroundSheetOpen = false })
    }

    if (moreSheetOpen) {
        StudioToolsSheet(
            onDismiss = { moreSheetOpen = false },
            onDestination = { destination ->
                moreSheetOpen = false
                if (destination == SceneDeckDestination.Onboarding) backStack.add(destination)
                else selectTopLevel(destination)
            },
            onBackgroundSettings = {
                moreSheetOpen = false
                backgroundSheetOpen = true
            },
        )
    }
}

private val SceneDeckDestination.description: String
    @Composable
    get() =
        stringResource(
            when (this) {
                SceneDeckDestination.Inventory -> R.string.inventory_description
                SceneDeckDestination.Graph -> R.string.graph_description
                SceneDeckDestination.Doctor -> R.string.doctor_description
                SceneDeckDestination.Connections -> R.string.connections_description
                SceneDeckDestination.Settings -> R.string.settings_description
                else -> R.string.help_description
            }
        )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioToolsSheet(
    onDismiss: () -> Unit,
    onDestination: (SceneDeckDestination) -> Unit,
    onBackgroundSettings: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            StudioSectionHeader(
                stringResource(R.string.studio_tools),
                Modifier.padding(horizontal = 24.dp),
            )
            (SceneDeckDestination.overflow + SceneDeckDestination.Onboarding).forEach { destination
                ->
                ListItem(
                    leadingContent = {
                        StudioIconWell(
                            destination.icon,
                            tone =
                                when (destination) {
                                    SceneDeckDestination.Connections -> StudioTone.SECONDARY
                                    SceneDeckDestination.Settings -> StudioTone.TERTIARY
                                    SceneDeckDestination.Doctor -> StudioTone.SUCCESS
                                    else -> StudioTone.PRIMARY
                                },
                        )
                    },
                    supportingContent = { Text(destination.description) },
                    modifier = Modifier.clickable { onDestination(destination) },
                ) {
                    Text(destination.label)
                }
            }
            ListItem(
                supportingContent = { Text(stringResource(R.string.session_behavior_description)) },
                leadingContent = {
                    StudioIconWell(SceneDeckIcons.Connections, tone = StudioTone.SECONDARY)
                },
                modifier = Modifier.clickable(onClick = onBackgroundSettings),
            ) {
                Text(stringResource(R.string.session_behavior))
            }
        }
    }
}

private fun NavBackStack<NavKey>.selectTopLevel(destination: SceneDeckDestination) {
    if (lastOrNull() == destination) return
    while (size > 1) removeAt(lastIndex)
    if (destination != SceneDeckDestination.Live) add(destination)
}
