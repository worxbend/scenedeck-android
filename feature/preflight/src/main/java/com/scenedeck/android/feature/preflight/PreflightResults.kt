package com.scenedeck.android.feature.preflight

import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.scenedeck.android.core.data.PreflightFinding
import com.scenedeck.android.core.data.PreflightReport
import com.scenedeck.android.core.data.PreflightStatus
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PreflightReportHeader(report: PreflightReport) {
    val date =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
            .format(Date(report.completedAtMs))
    Text("Checked $date", style = MaterialTheme.typography.titleSmall)
    Text("Snapshot only · re-run after changes.", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun PreflightResultRow(finding: PreflightFinding) {
    val label =
        when (finding.status) {
            PreflightStatus.PASS -> "Pass"
            PreflightStatus.FAIL -> "Fail"
            PreflightStatus.UNKNOWN -> "Unknown"
        }
    ListItem(
        headlineContent = { Text(finding.title) },
        supportingContent = { Text(finding.detail) },
        trailingContent = {
            Text(
                label,
                color =
                    if (finding.status == PreflightStatus.FAIL) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "$label. ${finding.title}. ${finding.detail}"
            },
    )
}
