@file:OptIn(ExperimentalMaterial3Api::class)

package app.workbid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.workbid.data.Report

/** Lets the app owner review reports without opening the Firebase console. Only admins get here. */
@Composable
fun ReportsScreen(vm: AppViewModel, snackbar: SnackbarHostState, back: () -> Unit) {
    var showClosed by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Report?>(null) }
    val open = vm.reports.filter { it.status == "new" }
    val closed = vm.reports.filter { it.status != "new" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Review reports") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NextStepCard(
                    "How to handle a report",
                    "Read what was reported. If it breaks your terms (spam, abuse, fake jobs, sharing personal info), remove it. " +
                        "Otherwise mark it reviewed. Google expects you to act on reports within a few days.",
                )
            }
            if (open.isEmpty()) item {
                EmptyState("No new reports", "When someone reports a job or bid, it shows up here.", icon = Icons.Default.CheckCircle)
            } else {
                item { GroupLabel("New · ${open.size}") }
                items(open, key = { it.id }) { r ->
                    ReportCard(vm, r, onRemove = { confirmRemove = r }, onReviewed = { vm.markReport(r, "reviewed") })
                }
            }
            if (closed.isNotEmpty()) item {
                TextButton(onClick = { showClosed = !showClosed }) {
                    Text(if (showClosed) "Hide closed reports" else "Show closed reports (${closed.size})")
                }
            }
            if (showClosed) items(closed, key = { it.id }) { r ->
                ReportCard(vm, r, onRemove = null, onReviewed = null, onReopen = { vm.markReport(r, "new") })
            }
        }
    }

    confirmRemove?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(if (r.type == "bid") "Remove this bid?" else "Remove this job?") },
            text = {
                Text(
                    if (r.type == "bid") "The bid is deleted for everyone. The contractor isn't told why. This can't be undone."
                    else "The job and every bid on it are deleted for everyone. Only open jobs can be removed. This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.removeReported(r); confirmRemove = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReportCard(
    vm: AppViewModel,
    r: Report,
    onRemove: (() -> Unit)?,
    onReviewed: (() -> Unit)?,
    onReopen: (() -> Unit)? = null,
) {
    // null = still loading, "" = gone or unreadable
    var target by remember(r.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(r.id, r.status) { target = vm.reportTarget(r) ?: "" }

    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefText("${if (r.type == "bid") "Bid" else "Job"} · ${ago(r.createdAt)}", Modifier.weight(1f))
                val (bg, fg) = statusColors(
                    when (r.status) { "new" -> "open"; "removed" -> "lost"; else -> "complete" }
                )
                Pill(
                    when (r.status) { "new" -> "New"; "removed" -> "Removed"; else -> "Reviewed" }, bg, fg,
                )
            }
            Text(r.reason, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (r.details.isNotBlank()) Text("“${r.details}”", style = MaterialTheme.typography.bodyMedium)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(12.dp)) {
                    when (val t = target) {
                        null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        "" -> Muted("Already deleted, or no longer available.")
                        else -> Text(t, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                    }
                }
            }
            Muted("Reported by user ${r.by.take(8)}… · job ${r.jobId.take(8)}…")
            if (onRemove != null || onReviewed != null || onReopen != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onReviewed != null) OutlinedButton(onClick = onReviewed) { Text("Mark reviewed") }
                    if (onRemove != null && !target.isNullOrEmpty()) Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(if (r.type == "bid") "Remove bid" else "Remove job") }
                    if (onReopen != null) TextButton(onClick = onReopen) { Text("Reopen") }
                }
            }
        }
    }
}
