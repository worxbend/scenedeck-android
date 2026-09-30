package com.scenedeck.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.navigation.SceneLinkParser
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSceneLink(intent)
        setContent {
            val appState = rememberSceneDeckAppState(settings = appViewModel.settingsRepository)
            val connectionState by appViewModel.connectionState.collectAsStateWithLifecycle()
            val stripState by appViewModel.stripState.collectAsStateWithLifecycle()
            val onboardingCompleted by
                appViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            var skipToConnections by rememberSaveable { mutableStateOf(false) }

            SceneDeckTheme(
                family = appState.themeFamily,
                darkTheme = appState.isDarkTheme(isSystemInDarkTheme()),
                dynamicColor = appState.dynamicColor,
                motionLevel = appState.motionLevel,
            ) {
                SceneDeckApp(
                    appState = appState,
                    connectionState = connectionState,
                    stripState = stripState,
                    onboardingCompleted = onboardingCompleted,
                    skipToConnections = skipToConnections,
                    onOnboardingSkip = {
                        skipToConnections = true
                        appViewModel.completeOnboarding()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSceneLink(intent)
    }

    /** `scenedeck://scene/{name}` (automation/Tasker): connect if needed, then switch. */
    private fun handleSceneLink(intent: Intent?) {
        SceneLinkParser.sceneName(intent?.dataString)?.let(appViewModel::onSceneLink)
    }
}
