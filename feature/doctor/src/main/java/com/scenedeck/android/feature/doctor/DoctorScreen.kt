package com.scenedeck.android.feature.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DoctorFix
import com.scenedeck.android.core.data.DoctorIssue
import com.scenedeck.android.core.data.DoctorSeverity
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.icons.SceneIcon
import com.scenedeck.android.core.designsystem.icons.imageVector
import com.scenedeck.android.core.designsystem.theme.SceneDeckTheme
import com.scenedeck.android.core.model.ConnectionState

/** Doctor page — diagnostics report grouped by severity (FEATURE_SPEC §7). */
@Composable
fun DoctorScreen(
    modifier: Modifier = Modifier,
    viewModel: DoctorViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
    onNavigateToInventory: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val connection = uiState.connection) {
        is ConnectionState.Ready -> DoctorContent(
            uiState = uiState,
            onRefresh = viewModel::refresh,
            onFix = { issue ->
                when (val fix = issue.fix) {
                    is DoctorFix.RemoveStaleEntry -> viewModel.removeStaleEntry(fix.sceneName)
                    is DoctorFix.AssignRole -> onNavigateToInventory()
                    null -> Unit
                }
            },
            modifier = modifier,
        )

        else -> DisconnectedPlaceholder(
            connectionState = connection,
            onConnect = onNavigateToConnections,
        )
    }
}

@Composable
internal fun DoctorContent(
    uiState: DoctorUiState,
    onRefresh: () -> Unit,
    onFix: (DoctorIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Doctor",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (uiState.running) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            OutlinedButton(onClick = onRefresh, enabled = !uiState.running) {
                Text("Re-run")
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityChip(DoctorSeverity.ERROR, uiState.errorCount)
            SeverityChip(DoctorSeverity.WARNING, uiState.warningCount)
            SeverityChip(DoctorSeverity.INFO, uiState.infoCount)
        }
        Spacer(Modifier.height(16.dp))

        if (uiState.ranOnce && uiState.issues.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = SceneIcon.DOCTOR.imageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "All clear — no issues found.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.issues, key = { "${it.checkId}-${it.sceneName}-${it.title}" }) { issue ->
                    IssueRow(issue = issue, onFix = onFix)
                }
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: DoctorSeverity, count: Int) {
    val colors = SceneDeckTheme.colors
    val (label, color) = when (severity) {
        DoctorSeverity.ERROR -> "Errors" to colors.recording
        DoctorSeverity.WARNING -> "Warnings" to colors.warning
        DoctorSeverity.INFO -> "Info" to MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun IssueRow(issue: DoctorIssue, onFix: (DoctorIssue) -> Unit) {
    val colors = SceneDeckTheme.colors
    val (icon, tint) = when (issue.severity) {
        DoctorSeverity.ERROR -> SceneIcon.BELL to colors.recording
        DoctorSeverity.WARNING -> SceneIcon.BOLT to colors.warning
        DoctorSeverity.INFO -> SceneIcon.SPARKLES to MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = issue.severity.name,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = issue.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = issue.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                issue.fix?.let {
                    TextButton(onClick = { onFix(issue) }) {
                        Text(
                            when (it) {
                                is DoctorFix.RemoveStaleEntry -> "Remove entry"
                                is DoctorFix.AssignRole -> "Assign role in Inventory"
                            },
                        )
                    }
                }
            }
        }
    }
}
