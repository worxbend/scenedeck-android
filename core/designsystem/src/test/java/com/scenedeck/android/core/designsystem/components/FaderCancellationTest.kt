package com.scenedeck.android.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaderCancellationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cancelledDragRestoresExactInitialVolume() {
        val original = 1.5
        val volume = mutableStateOf(original)
        val previews = mutableListOf<Double>()
        val commits = mutableListOf<Double>()
        composeRule.setContent {
            SceneDeckTheme {
                FaderTrack(
                    volumeMul = volume.value,
                    enabled = true,
                    contentDescription = "Test fader",
                    onPreview = {
                        previews += it
                        volume.value = it
                    },
                    onCommit = {
                        commits += it
                        volume.value = it
                    },
                    modifier = Modifier.size(80.dp, 280.dp),
                )
            }
        }
        composeRule.onNodeWithContentDescription("Test fader").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f))
            moveBy(Offset(0f, 40f))
            cancel()
        }
        composeRule.runOnIdle {
            assertTrue(previews.isNotEmpty())
            assertEquals(listOf(original), commits)
            assertEquals(original, volume.value, 0.0)
        }
    }
}
