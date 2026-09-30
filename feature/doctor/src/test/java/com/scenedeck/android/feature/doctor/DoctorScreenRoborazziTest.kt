package com.scenedeck.android.feature.doctor

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.scenedeck.android.core.data.DoctorFix
import com.scenedeck.android.core.data.DoctorIssue
import com.scenedeck.android.core.data.DoctorSeverity
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden shot of the Doctor report with one issue per severity (dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DoctorScreenRoborazziTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun doctorReportDark() {
        val state =
            DoctorUiState(
                connection = ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "linux")),
                running = false,
                ranOnce = true,
                issues =
                    listOf(
                        DoctorIssue(
                            severity = DoctorSeverity.ERROR,
                            checkId = "cycle",
                            title = "Circular scene reference",
                            detail = "These scenes reference each other in a loop: A → B.",
                        ),
                        DoctorIssue(
                            severity = DoctorSeverity.WARNING,
                            checkId = "stale-entry",
                            title = "Stale registry entry",
                            detail = "“Ghost” has curation metadata but no matching OBS scene.",
                            sceneName = "Ghost",
                            fix = DoctorFix.RemoveStaleEntry("Ghost"),
                        ),
                        DoctorIssue(
                            severity = DoctorSeverity.INFO,
                            checkId = "unassigned-role",
                            title = "No role assigned",
                            detail = "“Quiet A” has no registry entry and is treated as Primary.",
                            sceneName = "Quiet A",
                            fix = DoctorFix.AssignRole("Quiet A"),
                        ),
                    ),
            )
        composeRule.setContent {
            SceneDeckTheme(family = ThemeFamily.SCENEDECK, darkTheme = true) {
                Surface {
                    DoctorContent(uiState = state, onRefresh = {}, onFix = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("doctor_report_dark.png")
    }
}
