@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.workbid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.workbid.data.Bid
import app.workbid.data.HomeJob
import app.workbid.data.LineItem
import app.workbid.data.WorkOrder

// ---------------- job detail ----------------
@Composable
fun JobDetailScreen(
    vm: AppViewModel,
    jobId: String,
    snackbar: SnackbarHostState,
    back: () -> Unit,
    go: (Screen) -> Unit,
) {
    val job = vm.job(jobId)
    if (job == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val isOwner = job.postedBy == vm.uid
    val isPro = job.contractorId.isNotBlank() && job.contractorId == vm.uid
    val bids = vm.bidsFor(job.id)
    val myBid = vm.myBid(job.id)
    val wos = vm.workOrdersFor(job.id)
    val pendingWos = wos.count { !it.approved }

    var confirmAccept by remember { mutableStateOf<Bid?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmWithdraw by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    var editSchedule by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf<Triple<String, String, String>?>(null) } // type, id, label

    val canAct = (isOwner || isPro) && job.status in listOf("scheduled", "in_progress")

    // The single most useful thing this person can do right now, pinned to the bottom of the screen.
    val primary: Pair<String, () -> Unit>? = when {
        job.status == "open" && !isOwner && myBid == null -> "Place a bid" to { go(Screen.BidForm(job.id)) }
        job.status == "open" && !isOwner && myBid != null -> "Edit your bid" to { go(Screen.BidForm(job.id)) }
        job.status == "scheduled" && isPro -> "Start work" to { vm.setStatus(job, "in_progress") }
        job.status == "in_progress" && isPro -> "Mark complete" to { confirmComplete = true }
        else -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { RefText("${job.ref} · ${job.category}") },
                actions = {
                    StatusPill(job.status)
                    if (!isOwner) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Report this job") },
                                    leadingIcon = { Icon(Icons.Default.Warning, null) },
                                    onClick = { menu = false; reporting = Triple("job", job.id, "this job") },
                                )
                            }
                        }
                    } else Spacer(Modifier.width(12.dp))
                },
            )
        },
        bottomBar = {
            if (primary != null) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Button(onClick = primary.second, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(primary.first)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(job.title, style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Muted(job.area)
                    }
                }
            }
            item { Progress(job.status) }
            item { NextStep(job, isOwner, isPro, bids.size, myBid, pendingWos) }

            if (job.status != "open") {
                item {
                    Section("Booking") {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(job.company, fontWeight = FontWeight.Bold)
                                        Muted(
                                            if (isPro) "You · for ${job.postedByName.ifBlank { "the homeowner" }}"
                                            else job.contractorName
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        MoneyText(job.price, big = true)
                                        Muted("+ GST")
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                                    Text("${fdate(job.scheduledDate)} · ${job.window}", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (canAct) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editSchedule = true }) { Text("Change date or time") }
                                if (isOwner) OutlinedButton(onClick = { confirmComplete = true }) { Text("Mark complete") }
                            }
                        }
                    }
                }
            }

            item {
                Section("Job details") {
                    Fact("Budget", budgetText(job))
                    Fact("Timing", job.timing + if (job.preferDate.isNotBlank()) " · prefers ${fdate(job.preferDate)}" else "")
                    Fact("Posted by", (if (isOwner) "You" else job.postedByName.ifBlank { "Homeowner" }) + " · ${ago(job.createdAt)}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(job.description, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (job.status == "open") {
                if (isOwner) {
                    item {
                        Section("Bids · ${bids.size}") {
                            if (bids.isEmpty()) Muted("No bids yet. Contractors can see this job now; you'll see their bids here as they come in.")
                            else Muted("Sorted lowest price first. Look at the date and message too, not just the price.")
                            bids.forEach { b ->
                                BidCard(b, onAccept = { confirmAccept = b },
                                    onReport = { reporting = Triple("bid", b.id, "the bid from ${b.company}") })
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete this job", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (myBid != null) {
                    item {
                        Section("Your bid") {
                            BidCard(myBid, onAccept = null)
                            Muted("${bids.size} bid${if (bids.size == 1) "" else "s"} on this job so far.")
                            TextButton(onClick = { confirmWithdraw = true }) {
                                Text("Withdraw bid", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            } else {
                item {
                    Section("Work orders", onAdd = if (isPro && canAct) ({ go(Screen.WorkOrderForm(job.id)) }) else null) {
                        if (wos.isEmpty()) Muted(
                            if (isPro) "Break the job into line items with quantities and rates. The homeowner approves it here."
                            else "The contractor will add a work order with line items for you to approve."
                        )
                        wos.forEach { w ->
                            WorkOrderCard(job, w, canApprove = isOwner && !w.approved) { vm.approveWorkOrder(w) }
                        }
                    }
                }
                item {
                    val rc = vm.receiptsFor(job.id)
                    Section("Receipts", onAdd = if (canAct) ({ go(Screen.ReceiptForm(job.id)) }) else null) {
                        if (rc.isEmpty()) Muted("Log materials and other costs as they come in, so both of you can see the totals.")
                        rc.forEach { r ->
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.vendor, fontWeight = FontWeight.SemiBold)
                                    Muted(listOf(fdate(r.date), r.note).filter { it.isNotBlank() }.joinToString(" · "))
                                    Muted(
                                        "Added by " + when (r.by) {
                                            vm.uid -> "you"
                                            job.postedBy -> "homeowner"
                                            else -> "contractor"
                                        }
                                    )
                                }
                                MoneyText(r.amount)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (rc.isNotEmpty()) Row {
                            Text("Receipts total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            MoneyText(rc.sumOf { it.amount })
                        }
                    }
                }
            }
        }
    }

    confirmAccept?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmAccept = null },
            title = { Text("Book ${b.company}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fact("Price", "${money(b.amount)} + GST = ${money(b.amount * (1 + GST))}")
                    Fact("Date", "${fdate(b.availableDate)}, ${b.window}")
                    Text(
                        "The job closes to other bids. You can still change the date or time together afterwards.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { Button(onClick = { vm.acceptBid(job, b); confirmAccept = null }) { Text("Accept and book") } },
            dismissButton = { TextButton(onClick = { confirmAccept = null }) { Text("Not yet") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this job?") },
            text = { Text("The job and all ${bids.size} bid${if (bids.size == 1) "" else "s"} on it will be removed for everyone. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteJob(job); back() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    if (confirmWithdraw && myBid != null) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("Withdraw your bid?") },
            text = { Text("The homeowner won't see it any more. You can send a new bid while the job is still open.") },
            confirmButton = {
                Button(
                    onClick = { confirmWithdraw = false; vm.withdrawBid(myBid) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Withdraw") }
            },
            dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text("Keep bid") } },
        )
    }
    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text("Mark this job complete?") },
            text = {
                Text(
                    if (pendingWos > 0) "$pendingWos work order${if (pendingWos == 1) " is" else "s are"} still waiting for approval. " +
                        "Once complete, nobody can add work orders or receipts."
                    else "Once complete, nobody can add work orders or receipts."
                )
            },
            confirmButton = {
                Button(onClick = { confirmComplete = false; vm.setStatus(job, "complete") }) { Text("Mark complete") }
            },
            dismissButton = { TextButton(onClick = { confirmComplete = false }) { Text("Not yet") } },
        )
    }
    reporting?.let { (type, id, label) ->
        ReportDialog(label, onDismiss = { reporting = null }) { reason, details ->
            vm.report(type, id, job.id, reason, details)
            reporting = null
        }
    }
    if (editSchedule) {
        var date by remember { mutableStateOf(job.scheduledDate) }
        var window by remember { mutableStateOf(job.window.takeIf { it in WINDOWS } ?: WINDOWS.first()) }
        AlertDialog(
            onDismissRequest = { editSchedule = false },
            title = { Text("Change date or time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Agree on the change with the other person first. Both of you will see it right away.")
                    DateField("Date", date, { date = it })
                    Picker("Arrival window", window, WINDOWS, { window = it })
                }
            },
            confirmButton = {
                Button(onClick = { vm.updateSchedule(job, date, window); editSchedule = false }, enabled = date.isNotBlank()) {
                    Text("Save booking")
                }
            },
            dismissButton = { TextButton(onClick = { editSchedule = false }) { Text("Cancel") } },
        )
    }
}

/** Plain-language guidance for whoever is looking at this job, at every stage. */
@Composable
private fun NextStep(job: HomeJob, isOwner: Boolean, isPro: Boolean, bidCount: Int, myBid: Bid?, pendingWos: Int) {
    val (title, body) = when (job.status) {
        "open" -> when {
            isOwner && bidCount == 0 -> "Waiting for bids" to
                "Contractors can see your job now. Most bids arrive within a day or two. A clear description and photos-worth of detail gets more bids."
            isOwner -> "Compare and choose" to
                "You have $bidCount bid${if (bidCount == 1) "" else "s"}. Check the price, the date they can come and their message, then accept one to book it."
            myBid != null -> "Bid sent" to
                "The homeowner is reviewing bids. You can edit or withdraw yours until they accept one."
            else -> "Interested?" to
                "Send your price before GST, how many days it'll take, and the date you can be there."
        }
        "scheduled" -> when {
            isPro -> "You're booked" to
                "Be there ${fdate(job.scheduledDate)}, ${job.window}. Tap Start work when you arrive. Need a different time? Agree with the homeowner, then change it here."
            isOwner -> "Booked" to
                "${job.company} is coming ${fdate(job.scheduledDate)}, ${job.window}. They'll send a work order with line items for you to approve."
            else -> "No longer open" to "This job has been booked with another contractor."
        }
        "in_progress" -> when {
            isPro -> "Work in progress" to
                "Add a work order so the homeowner can approve costs, and log material receipts. Mark complete when you're done."
            isOwner && pendingWos > 0 -> "Approve the work order" to
                "$pendingWos work order${if (pendingWos == 1) " is" else "s are"} waiting for you below. Check the line items, then approve."
            isOwner -> "Work in progress" to
                "${job.company} has started. Work orders and receipts show up below as they're added."
            else -> "No longer open" to "This job has been booked with another contractor."
        }
        else -> "Complete" to
            (if (isOwner || isPro) "This job is done. Its work orders and receipts stay here for your records."
            else "This job has been completed.")
    }
    NextStepCard(title, body)
}

@Composable
private fun Progress(status: String) {
    val steps = listOf("Bidding", "Booked", "Working", "Done")
    val at = listOf("open", "scheduled", "in_progress", "complete").indexOf(status)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, s ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s, style = MaterialTheme.typography.labelMedium,
                    color = if (i <= at) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == at) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = if (i <= at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(
            label.uppercase(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp).padding(top = 2.dp),
        )
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReportDialog(label: String, onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $label") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What's wrong? Reports are private. The other person isn't told who reported them.",
                    style = MaterialTheme.typography.bodyMedium)
                REPORT_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == r, onClick = { reason = r }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(r)
                    }
                }
                OutlinedTextField(details, { details = it.take(500) }, label = { Text("Details (optional)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { reason?.let { onSend(it, details) } }, enabled = reason != null) { Text("Send report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BidCard(b: Bid, onAccept: (() -> Unit)?, onReport: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.company, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Muted("${b.contractorName.ifBlank { "Contractor" }} · sent ${ago(b.createdAt)}")
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(b.amount, big = true)
                    Muted("+ GST")
                }
            }
            Text(
                "Can come ${fdate(b.availableDate)}, ${b.window} · about ${trimNum(b.days)} day${if (b.days == 1.0) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (b.message.isNotBlank()) Text("“${b.message}”", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onAccept != null || onReport != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onAccept != null) Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept this bid") }
                    if (onReport != null) TextButton(onClick = onReport) {
                        Text("Report", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkOrderCard(job: HomeJob, w: WorkOrder, canApprove: Boolean, approve: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefText("${job.ref}-WO${w.number}", Modifier.weight(1f))
                val (bg, fg) = statusColors(if (w.approved) "complete" else "open")
                Pill(if (w.approved) "Approved" else "Needs approval", bg, fg)
            }
            w.lines.forEach { l ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(l.desc, style = MaterialTheme.typography.bodyMedium)
                        Muted("${trimNum(l.qty)} × ${money(l.rate)}")
                    }
                    Text(money(l.total), style = moneyStyle())
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Totals(w.subtotal)
            if (w.notes.isNotBlank()) Muted(w.notes)
            if (canApprove) Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Approve work order") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Approve ${money(w.subtotal * (1 + GST))}?") },
            text = { Text("You're agreeing to these line items and the total including GST. Approval can't be undone.") },
            confirmButton = { Button(onClick = { confirm = false; approve() }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

fun trimNum(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
private fun Totals(subtotal: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TotalRow("Subtotal", subtotal, false)
        TotalRow("GST ${(GST * 100).toInt()}%", subtotal * GST, false)
        TotalRow("Total", subtotal * (1 + GST), true)
    }
}

@Composable
private fun TotalRow(label: String, v: Double, bold: Boolean) {
    Row {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(money(v), style = moneyStyle(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

// ---------------- forms ----------------
@Composable
private fun FormScaffold(
    title: String,
    subtitle: String?,
    back: () -> Unit,
    submitLabel: String,
    error: String?,
    onSubmit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.Close, contentDescription = "Cancel") } },
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.ExtraBold)
                        if (subtitle != null) RefText(subtitle)
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(submitLabel) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun PostJobForm(vm: AppViewModel, back: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CATS.first()) }
    var area by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var timing by rememberSaveable { mutableStateOf(TIMING.first()) }
    var date by rememberSaveable { mutableStateOf(plusDays(7)) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val mn = min.toDoubleOrNull() ?: 0.0
    val mx = max.toDoubleOrNull() ?: 0.0
    val titleErr = if (tried && title.isBlank()) "Say what needs doing" else null
    val areaErr = if (tried && area.isBlank()) "Add your neighbourhood so contractors know if it's nearby" else null
    val descErr = if (tried && desc.trim().length < 20) "Add a bit more detail (at least 20 characters)" else null
    val budgetErr = if (mx > 0 && mn > mx) "The top of your budget is lower than the bottom" else null
    val dateErr = if (date < today()) "Pick today or a later date" else null
    val valid = title.isNotBlank() && area.isNotBlank() && desc.trim().length >= 20 && budgetErr == null && dateErr == null

    FormScaffold(
        "Post a job", null, back, "Post job",
        if (tried && !valid) "Fix the highlighted fields above." else null,
        onSubmit = {
            tried = true
            if (valid) {
                vm.postJob(title, category, area, desc, mn, mx, timing, date)
                back()
            }
        },
    ) {
        FormField(title, { title = it }, "What needs doing?", 90, placeholder = "e.g. Fix sticking patio door", error = titleErr)
        Picker("Trade", category, CATS, { category = it }, Modifier.fillMaxWidth())
        FormField(area, { area = it }, "Neighbourhood, city", 60, placeholder = "e.g. Fairfield, Victoria", error = areaErr)
        FormField(
            desc, { desc = it }, "Details", 2000, minLines = 4, showCount = true, error = descErr,
            placeholder = "Size, materials, access, parking, pets. Anything a contractor needs to give you an accurate price.",
        )
        Text("Budget (optional)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(min, { min = it }, "From", Modifier.weight(1f), error = budgetErr)
            MoneyField(max, { max = it }, "To", Modifier.weight(1f))
        }
        Picker("How soon?", timing, TIMING, { timing = it }, Modifier.fillMaxWidth())
        DateField("Preferred start", date, { date = it }, Modifier.fillMaxWidth())
        if (dateErr != null) Text(dateErr, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text(
            "Don't put your street address or phone number in the job. Share them with the contractor you book.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BidFormScreen(vm: AppViewModel, jobId: String, back: () -> Unit) {
    val job = vm.job(jobId)
    if (job == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val existing = remember { vm.myBid(jobId) }
    var company by rememberSaveable { mutableStateOf(existing?.company ?: vm.lastCompany()) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let { trimNum(it) } ?: "") }
    var days by rememberSaveable { mutableStateOf(existing?.days?.let { trimNum(it) } ?: "1") }
    var date by rememberSaveable { mutableStateOf(existing?.availableDate ?: job.preferDate.takeIf { it >= today() } ?: plusDays(3)) }
    var window by rememberSaveable { mutableStateOf(existing?.window?.takeIf { it in WINDOWS } ?: WINDOWS.first()) }
    var note by rememberSaveable { mutableStateOf(existing?.message ?: "") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val amt = amount.toDoubleOrNull()
    val d = days.toDoubleOrNull()
    val companyErr = if (tried && company.isBlank()) "Add your business name" else null
    val amountErr = if (tried && (amt == null || amt <= 0)) "Enter your price" else null
    val daysErr = if (tried && (d == null || d <= 0 || d > 365)) "1 to 365" else null
    val dateErr = if (date < today()) "Pick today or a later date" else null
    val valid = company.isNotBlank() && amt != null && amt > 0 && d != null && d > 0 && d <= 365 && dateErr == null

    FormScaffold(
        if (existing != null) "Edit your bid" else "Place a bid", "${job.ref} · ${job.title}",
        back, if (existing != null) "Update bid" else "Send bid",
        if (tried && !valid) "Fix the highlighted fields above." else null,
        onSubmit = {
            tried = true
            if (valid) {
                vm.placeBid(job, company, amt!!, d!!, date, window, note)
                back()
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Homeowner's budget: ${budgetText(job)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${job.area} · ${job.timing}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FormField(company, { company = it }, "Business name", 60, placeholder = "e.g. Coastline Plumbing Ltd.", error = companyErr)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(amount, { amount = it }, "Price before GST", Modifier.weight(1.5f), error = amountErr,
                help = amt?.takeIf { it > 0 }?.let { "${money(it * (1 + GST))} with GST" })
            OutlinedTextField(
                days, { v -> days = v.filter { it.isDigit() || it == '.' }.take(5) },
                label = { Text("Days") }, singleLine = true, isError = daysErr != null,
                supportingText = daysErr?.let { { Text(it) } },
                keyboardOptions = decimalKeys, modifier = Modifier.weight(1f),
            )
        }
        DateField("Date you can be there", date, { date = it }, Modifier.fillMaxWidth())
        if (dateErr != null) Text(dateErr, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Picker("Arrival window", window, WINDOWS, { window = it }, Modifier.fillMaxWidth())
        FormField(
            note, { note = it }, "Message to homeowner", 1000, minLines = 3, showCount = true,
            placeholder = "What's included, materials, licences and insurance, warranty.",
        )
    }
}

private data class LineDraft(val desc: String = "", val qty: String = "1", val rate: String = "")

@Composable
fun WorkOrderFormScreen(vm: AppViewModel, jobId: String, back: () -> Unit) {
    val job = vm.job(jobId)
    if (job == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val lines = remember { mutableStateListOf(LineDraft(), LineDraft()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed = lines.mapNotNull { d ->
        val q = d.qty.toDoubleOrNull() ?: 0.0
        val r = d.rate.toDoubleOrNull() ?: 0.0
        if (d.desc.isBlank() || q <= 0 || r <= 0) null else LineItem(d.desc.trim(), q, r)
    }

    FormScaffold(
        "New work order", "${job.ref}-WO${vm.workOrdersFor(job.id).size + 1}", back, "Send for approval", error,
        onSubmit = {
            error = if (parsed.isEmpty()) "Add at least one line with a description, quantity and rate." else null
            if (error == null) {
                vm.addWorkOrder(job, parsed, notes)
                back()
            }
        },
    ) {
        Text(
            "List what you'll charge for. The homeowner sees each line and approves the total.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lines.forEachIndexed { i, d ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RefText("Line ${i + 1}", Modifier.weight(1f))
                        if (lines.size > 1) IconButton(onClick = { lines.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove line ${i + 1}")
                        }
                    }
                    FormField(d.desc, { lines[i] = d.copy(desc = it) }, "Description", 120,
                        placeholder = if (i == 0) "e.g. Materials: vanity, tile" else "e.g. Labour")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            d.qty, { v -> lines[i] = d.copy(qty = v.filter { it.isDigit() || it == '.' }.take(7)) },
                            label = { Text("Qty or hours") }, singleLine = true,
                            keyboardOptions = decimalKeys, modifier = Modifier.weight(1f),
                        )
                        MoneyField(d.rate, { lines[i] = d.copy(rate = it) }, "Rate", Modifier.weight(1f))
                    }
                }
            }
        }
        if (lines.size < 50) OutlinedButton(onClick = { lines.add(LineDraft()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add line")
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) { Totals(parsed.sumOf { it.total }) }
        }
        FormField(notes, { notes = it }, "Notes", 1000, minLines = 3, placeholder = "Scope, exclusions, warranty.")
    }
}

@Composable
fun ReceiptFormScreen(vm: AppViewModel, jobId: String, back: () -> Unit) {
    val job = vm.job(jobId)
    if (job == null) {
        LaunchedEffect(Unit) { back() }
        return
    }
    var vendor by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today()) }
    var note by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val amt = amount.toDoubleOrNull()
    val vendorErr = if (tried && vendor.isBlank()) "Where was it bought?" else null
    val amountErr = if (tried && (amt == null || amt <= 0)) "Enter the amount" else null
    val valid = vendor.isNotBlank() && amt != null && amt > 0

    FormScaffold(
        "Add a receipt", job.ref, back, "Add receipt",
        if (tried && !valid) "Fix the highlighted fields above." else null,
        onSubmit = {
            tried = true
            if (valid) {
                vm.addReceipt(job, vendor, amt!!, date, note)
                back()
            }
        },
    ) {
        FormField(vendor, { vendor = it }, "Vendor", 60, placeholder = "e.g. RONA, Andrew Sheret", error = vendorErr)
        MoneyField(amount, { amount = it }, "Amount including tax", Modifier.fillMaxWidth(), error = amountErr)
        DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
        FormField(note, { note = it }, "What was it for?", 120, placeholder = "e.g. 2 shut-off valves, braided lines")
        Text(
            "Keep the paper receipt too. Workbid records the amount, not a photo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
