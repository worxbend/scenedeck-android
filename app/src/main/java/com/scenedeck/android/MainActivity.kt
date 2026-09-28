package com.scenedeck.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val appState = rememberSceneDeckAppState(settings = viewModel.settingsRepository)
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
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
                    onboardingCompleted = onboardingCompleted,
                    skipToConnections = skipToConnections,
                    onOnboardingSkip = {
                        skipToConnections = true
                        viewModel.completeOnboarding()
                    },
                )
            }
        }
    }
}
