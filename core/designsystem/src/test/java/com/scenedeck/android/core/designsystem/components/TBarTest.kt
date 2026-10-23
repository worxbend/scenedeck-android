package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.scenedeck.android.core.designsystem.theme.MotionLevel
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TBarTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun fractionFollowsAbsoluteThumbCentre() {
        // 400 px track, 100 px thumb → 300 px travel.
        assertEquals(0f, tBarFraction(50f, 400f, 100f), 0.0001f)
        assertEquals(0.5f, tBarFraction(200f, 400f, 100f), 0.0001f)
        assertEquals(1f, tBarFraction(350f, 400f, 100f), 0.0001f)
        assertEquals(0f, tBarFraction(-20f, 400f, 100f), 0.0001f)
        assertEquals(1f, tBarFraction(999f, 400f, 100f), 0.0001f)
        assertEquals(0f, tBarFraction(10f, 80f, 100f), 0.0001f)
    }

    @Test
    fun onlyPressesNearTheRestingThumbArm() {
        assertTrue(tBarGrabAllowed(10f, 400f, 100f))
        assertTrue(tBarGrabAllowed(100f, 400f, 100f))
        assertTrue(tBarGrabAllowed(125f, 400f, 100f))
        assertFalse(tBarGrabAllowed(300f, 400f, 100f))
    }

    @Test
    fun fullLengthDragWithinTheControlFires() {
        var fired = 0
        setTBar { fired++ }
        tBar().performTouchInput {
            swipe(start = centerLeft.copy(x = 20f), end = centerRight.copy(x = right - 20f))
        }
        composeRule.waitForIdle()
        assertEquals(1, fired)
    }

    @Test
    fun shortDragSpringsBackWithoutFiring() {
        var fired = 0
        setTBar { fired++ }
        tBar().performTouchInput {
            swipe(start = centerLeft.copy(x = 20f), end = center)
        }
        composeRule.waitForIdle()
        assertEquals(0, fired)
    }

    @Test
    fun dragStartingAtTheFarEndIsIgnored() {
        var fired = 0
        setTBar { fired++ }
        tBar().performTouchInput {
            swipe(start = centerRight.copy(x = right - 40f), end = centerRight.copy(x = right - 5f))
        }
        composeRule.waitForIdle()
        assertEquals(0, fired)
    }

    @Test
    fun screenReaderCanFireTransition() {
        var fired = 0
        setTBar { fired++ }
        val node = tBar().fetchSemanticsNode()
        assertTrue(node.config[SemanticsProperties.ContentDescription].single().contains("release"))
        node.config[SemanticsActions.CustomActions].single().action()
        assertEquals(1, fired)
    }

    private fun tBar() = composeRule.onNode(hasContentDescription("T-bar", substring = true))

    private fun setTBar(onTrigger: () -> Unit) {
        composeRule.setContent {
            SceneDeckTheme(darkTheme = true) {
                TBar(
                    motionLevel = MotionLevel.OFF,
                    onTrigger = onTrigger,
                    modifier = Modifier.width(320.dp),
                )
            }
        }
    }
}
