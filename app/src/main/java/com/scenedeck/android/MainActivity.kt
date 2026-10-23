package com.scenedeck.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.designsystem.components.LocalRetryNow
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
        // Only fresh launches deliver the link; recreation must not re-fire the scene switch.
        if (savedInstanceState == null) handleSceneLink(intent)
        setContent {
            val appState = rememberSceneDeckAppState(settings = appViewModel.settingsRepository)
            val connectionState by appViewModel.connectionState.collectAsStateWithLifecycle()
            val stripState by appViewModel.stripState.collectAsStateWithLifecycle()
            val onboardingCompleted by
                appViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val uiDensity by appViewModel.uiDensity.collectAsStateWithLifecycle()
            var skipToConnections by rememberSaveable { mutableStateOf(false) }
            val darkTheme = appState.isDarkTheme(isSystemInDarkTheme())
            // Bar icons follow the resolved app theme, not the system night mode.
            // Applied when the first frame commits (not a launched coroutine one frame later).
            DisposableEffect(darkTheme) {
                applySystemBarAppearance(darkTheme)
                onDispose {}
            }
            val sceneLinkMessage by appViewModel.sceneLinkMessage.collectAsStateWithLifecycle()
            // Toast (not a shell snackbar) so deep-link failures surface on every screen,
            // including onboarding.
            LaunchedEffect(sceneLinkMessage) {
                sceneLinkMessage?.let { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    appViewModel.onSceneLinkMessageShown(message)
                }
            }
            val retryNow = remember { { appViewModel.retryNow() } }

            SceneDeckTheme(
                family = appState.themeFamily,
                darkTheme = darkTheme,
                dynamicColor = appState.dynamicColor,
                motionLevel = appState.motionLevel,
                density = uiDensity,
            ) {
                CompositionLocalProvider(LocalRetryNow provides retryNow) {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSceneLink(intent)
    }

    /** `scenedeck://scene/{name}` (automation/Tasker): connect if needed, then switch. */
    private fun handleSceneLink(intent: Intent?) {
        if (intent == null) return
        SceneLinkParser.sceneName(intent.dataString)?.let(appViewModel::onSceneLink)
        intent.data = null
    }
}
