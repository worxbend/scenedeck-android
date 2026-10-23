package com.scenedeck.android.feature.doctor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scenedeck.android.core.designsystem.components.StudioIconWell
import com.scenedeck.android.core.designsystem.components.StudioTone
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector

// Whole-page Doctor states shown instead of the report: running, all clear and failed.

/** First run in progress: no report exists yet. */
@Composable
internal fun DoctorRunningState() {
    DoctorStatusPanel(
        title = "Running checks…",
        body = "Reading scenes, roles and audio sources from OBS.",
        leading = {
            CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
        },
    )
}

/** Successful run without findings. */
@Composable
internal fun DoctorAllClearState() {
    DoctorStatusPanel(
        title = "All clear — no issues found",
        body = "Roles, scene dependencies and audio sources look healthy.",
        leading = {
            StudioIconWell(
                icon = SceneIcon.DOCTOR.imageVector,
                tone = StudioTone.SUCCESS,
                modifier = Modifier.size(56.dp),
            )
        },
    )
}

/** Failed run: sanitized message only; earlier results are not shown as current. */
@Composable
internal fun DoctorFailedState(message: String, onRetry: () -> Unit) {
    DoctorStatusPanel(
        title = "Checks didn't complete",
        body = message,
        leading = {
            StudioIconWell(
                icon = SceneIcon.BELL.imageVector,
                tone = StudioTone.WARNING,
                modifier = Modifier.size(56.dp),
            )
        },
        action = { FilledTonalButton(onClick = onRetry) { Text("Try again") } },
    )
}

@Composable
private fun DoctorStatusPanel(
    title: String,
    body: String,
    leading: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        leading()
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
