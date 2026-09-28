package com.scenedeck.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = rememberSceneDeckAppState()
            SceneDeckTheme(
                family = appState.themeFamily,
                darkTheme = appState.isDarkTheme(isSystemInDarkTheme()),
                dynamicColor = appState.dynamicColor,
                motionLevel = appState.motionLevel,
            ) {
                SceneDeckApp(appState = appState)
            }
        }
    }
}
