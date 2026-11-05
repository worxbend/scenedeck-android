package com.scenedeck.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
import com.scenedeck.android.core.designsystem.components.LocalSimulation
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.demo.AppProcess
import com.scenedeck.android.demo.DemoActivity
import com.scenedeck.android.demo.DemoEntryDialog
import com.scenedeck.android.demo.DemoFrame
import com.scenedeck.android.demo.DemoViewModel
import com.scenedeck.android.demo.LocalExitDemo
import com.scenedeck.android.navigation.SceneLinkParser
import com.scenedeck.android.shortcuts.LocalShortcutHost
import com.scenedeck.android.shortcuts.ShortcutHostFeedback
import com.scenedeck.android.shortcuts.ShortcutHostViewModel
import com.scenedeck.android.shortcuts.ShortcutTextInputBoundary
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class MainActivity : ComponentActivity() {

    @Inject lateinit var appProcess: AppProcess

    private val appViewModel: AppViewModel by viewModels()
    private val shortcuts: ShortcutHostViewModel by viewModels()
    private val demo: DemoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        check((this is DemoActivity) == (appProcess == AppProcess.DEMO)) {
            "Activity process mismatch"
        }
        enableEdgeToEdge()
        // Only fresh launches deliver the link; recreation must not re-fire the scene switch.
        if (savedInstanceState == null) handleSceneLink(intent)
        setContent { ActivityContent() }
    }

    @Composable
    private fun ActivityContent() {
        val appState = rememberSceneDeckAppState(settings = appViewModel.settingsRepository)
        val uiDensity by appViewModel.uiDensity.collectAsStateWithLifecycle()
        val darkTheme = appState.isDarkTheme(isSystemInDarkTheme())
        DisposableEffect(darkTheme) {
            applySystemBarAppearance(darkTheme)
            onDispose {}
        }
        val sceneLinkMessage by appViewModel.sceneLinkMessage.collectAsStateWithLifecycle()
        LaunchedEffect(sceneLinkMessage) {
            sceneLinkMessage?.let { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                appViewModel.onSceneLinkMessageShown(message)
            }
        }
        val retryNow = remember { { appViewModel.retryNow() } }
        val exit = if (appProcess == AppProcess.DEMO) ::exitDemo else null
        SceneDeckTheme(
            family = appState.themeFamily,
            darkTheme = darkTheme,
            dynamicColor = appState.dynamicColor,
            motionLevel = appState.motionLevel,
            density = uiDensity,
        ) {
            CompositionLocalProvider(
                LocalRetryNow provides retryNow,
                LocalShortcutHost provides shortcuts.host,
                LocalSimulation provides (appProcess == AppProcess.DEMO),
                LocalExitDemo provides exit,
            ) {
                ShortcutTextInputBoundary(shortcuts.host.textInput) { HostedApp(appState) }
            }
        }
    }

    @Composable
    private fun HostedApp(appState: SceneDeckAppState) {
        val connectionState by appViewModel.connectionState.collectAsStateWithLifecycle()
        val stripState by appViewModel.stripState.collectAsStateWithLifecycle()
        val onboardingCompleted by appViewModel.onboardingCompleted.collectAsStateWithLifecycle()
        var skipToConnections by rememberSaveable { mutableStateOf(false) }
        var demoEntryOpen by remember { mutableStateOf(false) }
        val isDemo = appProcess == AppProcess.DEMO
        val content: @Composable () -> Unit = {
            SceneDeckApp(
                appState = appState,
                connectionState = connectionState,
                stripState = stripState,
                onboardingCompleted = if (isDemo) true else onboardingCompleted,
                skipToConnections = skipToConnections,
                onOnboardingSkip = {
                    shortcuts.host.admission.navigating()
                    skipToConnections = true
                    appViewModel.completeOnboarding()
                },
                onOpenDemo = {
                    shortcuts.host.admission.navigating()
                    demoEntryOpen = true
                },
            )
        }
        if (isDemo) {
            val phase by demo.state.collectAsStateWithLifecycle()
            DemoFrame(phase, ::exitDemo, demo::retry, content)
        } else content()
        ShortcutHostFeedback(shortcuts.host)
        if (demoEntryOpen && !isDemo)
            DemoEntryDialog(
                onOpen = {
                    demoEntryOpen = false
                    startActivity(DemoActivity.intent(this@MainActivity))
                },
                onDismiss = { demoEntryOpen = false },
            )
    }

    private fun exitDemo() {
        check(appProcess == AppProcess.DEMO)
        shortcuts.host.admission.navigating()
        demo.exit()
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        shortcuts.host.foreground(true)
    }

    override fun onPause() {
        shortcuts.host.foreground(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        shortcuts.host.focus(hasFocus)
    }

    // Public Activity hook inherited through AndroidX core's restricted compatibility base.
    // Keep superclass dispatch so focused children and system keys retain their normal path.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val editing = shortcuts.host.textInput.editing
        val childHandled = super.dispatchKeyEvent(event)
        return shortcuts.host.dispatch(event, childHandled, editing)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSceneLink(intent)
    }

    /** `scenedeck://scene/{name}` (automation/Tasker): connect if needed, then switch. */
    private fun handleSceneLink(intent: Intent?) {
        if (intent == null) return
        if (appProcess == AppProcess.LIVE)
            SceneLinkParser.sceneName(intent.dataString)?.let(appViewModel::onSceneLink)
        intent.data = null
    }
}
