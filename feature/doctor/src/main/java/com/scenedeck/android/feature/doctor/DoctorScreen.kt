package com.scenedeck.android.feature.doctor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenedeck.android.core.data.DoctorFix
import com.scenedeck.android.core.data.DoctorIssue
import com.scenedeck.android.core.data.DoctorSeverity
import com.scenedeck.android.core.designsystem.components.DisconnectedPlaceholder
import com.scenedeck.android.core.designsystem.components.StudioPageHeader
import com.scenedeck.android.core.designsystem.components.studioFilterChipColors
import com.scenedeck.android.core.designsystem.icons.SceneDeckIcons
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
        is ConnectionState.Ready ->
            DoctorContent(
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

        else ->
            DisconnectedPlaceholder(
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
    var severityFilter by remember { mutableStateOf<DoctorSeverity?>(null) }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        StudioPageHeader("Doctor", "Preflight your scene collection", SceneDeckIcons.Doctor) {
            // Progress shows in the body (running panel or report progress bar), not here.
            OutlinedButton(onClick = onRefresh, enabled = !uiState.running) {
                Text("Re-run")
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            uiState.error != null -> DoctorFailedState(message = uiState.error, onRetry = onRefresh)
            // No report yet, or an all-clear being re-checked: an earlier "all clear" must not
            // read as the result of the run in progress.
            !uiState.ranOnce || (uiState.running && uiState.issues.isEmpty()) ->
                DoctorRunningState()
            uiState.issues.isEmpty() -> DoctorAllClearState()
            else ->
                DoctorReport(
                    uiState = uiState,
                    severityFilter = severityFilter,
                    onFilterChange = { severityFilter = it },
                    onFix = onFix,
                )
        }
    }
}

@Composable
private fun DoctorReport(
    uiState: DoctorUiState,
    severityFilter: DoctorSeverity?,
    onFilterChange: (DoctorSeverity?) -> Unit,
    onFix: (DoctorIssue) -> Unit,
) {
    SeverityFilterRow(
        issues = uiState.issues,
        severityFilter = severityFilter,
        onFilterChange = onFilterChange,
    )
    // Re-running keeps the previous successful report visible, marked as refreshing.
    Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
        if (uiState.running) {
            LinearProgressIndicator(
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = "Refreshing checks"
                        liveRegion = LiveRegionMode.Polite
                    }
            )
        }
    }
    val visible = uiState.issues.filter { severityFilter == null || it.severity == severityFilter }
    if (visible.isEmpty()) {
        Text(
            text = "No ${severityFilter?.label()?.lowercase().orEmpty()} issues.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(visible, key = { "${it.checkId}-${it.sceneName}-${it.title}" }) { issue ->
            // Fixes act on the previous report; hold them until the refresh finishes.
            IssueRow(issue = issue, fixEnabled = !uiState.running, onFix = onFix)
        }
    }
}

@Composable
private fun SeverityFilterRow(
    issues: List<DoctorIssue>,
    severityFilter: DoctorSeverity?,
    onFilterChange: (DoctorSeverity?) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            colors = studioFilterChipColors(),
            border = null,
            selected = severityFilter == null,
            onClick = { onFilterChange(null) },
            label = { Text("All ${issues.size}") },
        )
        DoctorSeverity.entries.forEach { severity ->
            FilterChip(
                colors = studioFilterChipColors(),
                border = null,
                selected = severityFilter == severity,
                onClick = { onFilterChange(if (severityFilter == severity) null else severity) },
                label = { Text("${severity.label()} ${issues.count { it.severity == severity }}") },
            )
        }
    }
}

private fun DoctorSeverity.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun IssueRow(issue: DoctorIssue, fixEnabled: Boolean, onFix: (DoctorIssue) -> Unit) {
    val colors = SceneDeckTheme.colors
    val (icon, tint) =
        when (issue.severity) {
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
                    TextButton(onClick = { onFix(issue) }, enabled = fixEnabled) {
                        Text(
                            when (it) {
                                is DoctorFix.RemoveStaleEntry -> "Remove entry"
                                is DoctorFix.AssignRole -> "Assign role in Inventory"
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun DoctorClearPreview() {
    SceneDeckTheme {
        Surface {
            DoctorContent(uiState = DoctorUiState(ranOnce = true), onRefresh = {}, onFix = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun DoctorRunningPreview() {
    SceneDeckTheme {
        Surface {
            DoctorContent(uiState = DoctorUiState(running = true), onRefresh = {}, onFix = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.PreviewLightDark
@Composable
private fun DoctorFailedPreview() {
    SceneDeckTheme {
        Surface {
            DoctorContent(
                uiState = DoctorUiState(error = DOCTOR_FAILED_MESSAGE),
                onRefresh = {},
                onFix = {},
            )
        }
    }
}
