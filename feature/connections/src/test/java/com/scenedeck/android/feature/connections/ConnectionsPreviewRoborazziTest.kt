package com.scenedeck.android.feature.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ConnectionsPreviewRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(family: ThemeFamily, dark: Boolean, name: String) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProfileCard(
                            profile =
                                com.scenedeck.android.core.data.ConnectionProfile(
                                    1,
                                    "Studio desk",
                                    "192.168.1.20",
                                    4455,
                                    0,
                                ),
                            isLastUsed = true,
                            connectionState =
                                com.scenedeck.android.core.model.ConnectionState.Disconnected,
                            onConnect = {},
                            onDisconnect = {},
                            onEdit = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test
    fun sceneDeckDark() = capture(ThemeFamily.SCENEDECK, true, "connections_scenedeck_dark.png")

    @Test
    fun sceneDeckLight() = capture(ThemeFamily.SCENEDECK, false, "connections_scenedeck_light.png")

    @Test fun obsDark() = capture(ThemeFamily.OBS, true, "connections_obs_dark.png")

    private fun captureEditor(
        dark: Boolean,
        name: String,
        family: ThemeFamily = ThemeFamily.SCENEDECK,
    ) {
        composeRule.setContent {
            SceneDeckTheme(family = family, darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    ProfileEditForm(
                        profileId = null,
                        initialName = "Studio desk",
                        initialHost = "192.168.1.20",
                        initialPort = "4455",
                        initialPassword = "",
                        testState = TestConnectionState.Idle,
                        onTest = { _, _, _ -> },
                        onResetTest = {},
                        onSave = { _, _, _, _, _ -> },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(name)
    }

    @Test fun editorDark() = captureEditor(true, "connection_editor_scenedeck_dark.png")

    @Test fun editorObs() = captureEditor(true, "connection_editor_obs_dark.png", ThemeFamily.OBS)

    @Test fun editorLight() = captureEditor(false, "connection_editor_scenedeck_light.png")
}
