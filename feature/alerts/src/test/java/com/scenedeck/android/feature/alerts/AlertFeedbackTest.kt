package com.scenedeck.android.feature.alerts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scenedeck.android.core.data.LiveAlert
import com.scenedeck.android.core.data.LiveAlertKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertFeedbackTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    @Test
    fun onlyNewForegroundWarningsVibrateAfterOptIn() {
        val owner = Owner()
        val states = MutableStateFlow(alertsPreviewState())
        var haptics = 0
        val feedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics++
                }
            }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalHapticFeedback provides feedback,
            ) {
                AlertFeedbackEffect(states, true)
            }
        }
        compose.waitForIdle()
        assertEquals(0, haptics) // Existing warnings and default opt-out are silent.
        compose.runOnIdle { states.value = states.value.copy(hapticsEnabled = true) }
        compose.waitForIdle()
        assertEquals(0, haptics)
        fun next(id: Long) {
            compose.runOnIdle {
                states.value =
                    states.value.copy(alerts = listOf(LiveAlert(LiveAlertKind.DISK_SPACE, id)))
            }
            compose.waitForIdle()
        }
        next(3)
        assertEquals(1, haptics)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        next(4)
        assertEquals(1, haptics)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals(1, haptics) // Background warnings are not replayed on return.
        next(5)
        assertEquals(2, haptics)
    }
}
