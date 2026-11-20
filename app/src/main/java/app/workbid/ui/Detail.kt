@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.workbid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

    var confirmAccept by remember { mutableStateOf<Bid?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editSchedule by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                title = { RefText("${job.ref} · ${job.category}") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(job.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            item { Progress(job.status) }
            item {
                Section("Job") {
                    Fact("Location", job.area)
                    Fact("Budget", budgetText(job))
                    Fact("Timing", job.timing + if (job.preferDate.isNotBlank()) " · prefers ${fdate(job.preferDate)}" else "")
                    Fact("Homeowner", (if (isOwner) "You" else job.postedByName.ifBlank { "Homeowner" }) + " · posted ${ago(job.createdAt)}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(job.description, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (job.status == "open") {
                if (isOwner) {
                    item {
                        Section("Bids · ${bids.size}") {
                            if (bids.isEmpty()) Muted("No bids yet. Contractors can see this job now.")
                            bids.forEach { b -> BidCard(b, won = false, onAccept = { confirmAccept = b }) }
                        }
                    }
                    item {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete this job", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    item {
                        Section("Your bid · ${bids.size} so far") {
                            if (myBid != null) {
                                BidCard(myBid, won = false, onAccept = null)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { go(Screen.BidForm(job.id)) }) { Text("Edit bid") }
                                    TextButton(onClick = { vm.withdrawBid(myBid) }) {
                                        Text("Withdraw", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            } else {
                                Muted("Send your price, how long it'll take, and when you can be there.")
                                Button(onClick = { go(Screen.BidForm(job.id)) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Place a bid")
                                }
                            }
                        }
                    }
                }
            } else {
                val canAct = (isOwner || isPro) && job.status != "complete"
                item {
                    Section("Booked") {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(job.company, fontWeight = FontWeight.Bold)
                                        Muted(if (isPro) "You" else job.contractorName)
                                    }
                                    MoneyText(job.price, big = true)
                                }
                                Text("${fdate(job.scheduledDate)} · ${job.window}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (canAct) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editSchedule = true }) { Text("Change date or time") }
                                if (isPro && job.status == "scheduled") {
                                    Button(
                                        onClick = { vm.setStatus(job, "in_progress") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary,
                                        ),
                                    ) { Text("Start work") }
                                }
                                Button(onClick = { vm.setStatus(job, "complete") }) { Text("Mark complete") }
                            }
                        }
                    }
                }
                item {
                    val wos = vm.workOrdersFor(job.id)
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
                        if (rc.isEmpty()) Muted("Log materials and other costs as they come in.")
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
                            Text("Materials total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
            title = { Text("Accept ${b.company}?") },
            text = { Text("${money(b.amount)} + GST. They'll be booked for ${fdate(b.availableDate)}, ${b.window}. Other bids will be marked not selected.") },
            confirmButton = { Button(onClick = { vm.acceptBid(job, b); confirmAccept = null }) { Text("Accept bid") } },
            dismissButton = { TextButton(onClick = { confirmAccept = null }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this job?") },
            text = { Text("The job and all its bids will be removed for everyone.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteJob(job); back() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    if (editSchedule) {
        var date by remember { mutableStateOf(job.scheduledDate) }
        var window by remember { mutableStateOf(job.window.ifBlank { WINDOWS.first() }) }
        AlertDialog(
            onDismissRequest = { editSchedule = false },
            title = { Text("Change date or time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Both of you will see the new booking right away.")
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

@Composable
private fun Progress(status: String) {
    val steps = listOf("Bidding", "Scheduled", "In progress", "Complete")
    val at = listOf("open", "scheduled", "in_progress", "complete").indexOf(status)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, s ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s, style = MaterialTheme.typography.labelSmall,
                    color = if (i <= at) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == at) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = if (i <= at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().height(3.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp).padding(top = 2.dp),
        )
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BidCard(b: Bid, won: Boolean, onAccept: (() -> Unit)?) {
    Surface(
        color = if (won) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.company, fontWeight = FontWeight.Bold)
                    Muted("${b.contractorName.ifBlank { "Contractor" }} · sent ${ago(b.createdAt)}")
                }
                MoneyText(b.amount, big = true)
            }
            val days = if (b.days % 1.0 == 0.0) b.days.toInt().toString() else b.days.toString()
            Text(
                "Can be there ${fdate(b.availableDate)}, ${b.window} · about $days day${if (b.days == 1.0) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (b.message.isNotBlank()) Muted(b.message)
            if (onAccept != null) Button(onClick = onAccept) { Text("Accept bid") }
        }
    }
}

@Composable
private fun WorkOrderCard(job: HomeJob, w: WorkOrder, canApprove: Boolean, approve: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefText("${job.ref}-WO${w.number}", Modifier.weight(1f))
                val (bg, fg) = statusColors(if (w.approved) "complete" else "pending")
                Pill(if (w.approved) "Approved" else "Awaiting approval", bg, fg)
            }
            w.lines.forEach { l ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(l.desc, style = MaterialTheme.typography.bodyMedium)
                        Muted("${trimNum(l.qty)} × ${money(l.rate)}")
                    }
                    Text(money(l.total), fontFamily = FontFamily.Monospace)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Totals(w.subtotal)
            if (w.notes.isNotBlank()) Muted(w.notes)
            if (canApprove) Button(onClick = approve) { Text("Approve work order") }
        }
    }
}

private fun trimNum(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

@Composable
private fun Totals(subtotal: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TotalRow("Subtotal", subtotal, false)
        TotalRow("GST 5%", subtotal * GST, false)
        TotalRow("Total", subtotal * (1 + GST), true)
    }
}

@Composable
private fun TotalRow(label: String, v: Double, bold: Boolean) {
    Row {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(money(v), fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
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
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(submitLabel) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

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
    var error by remember { mutableStateOf<String?>(null) }

    FormScaffold("Post a job", null, back, "Post job", error, onSubmit = {
        val mn = min.toDoubleOrNull() ?: 0.0
        val mx = max.toDoubleOrNull() ?: 0.0
        error = when {
            title.isBlank() || area.isBlank() || desc.isBlank() -> "Add a title, your area and some details so contractors can price it."
            mx > 0 && mn > mx -> "The top of your budget is lower than the bottom."
            else -> null
        }
        if (error == null) {
            vm.postJob(title, category, area, desc, mn, mx, timing, date)
            back()
        }
    }) {
        OutlinedTextField(title, { title = it.take(90) }, label = { Text("What needs doing?") },
            placeholder = { Text("e.g. Fix sticking patio door") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Picker("Trade", category, CATS, { category = it }, Modifier.fillMaxWidth())
        OutlinedTextField(area, { area = it.take(60) }, label = { Text("Neighbourhood, city") },
            placeholder = { Text("e.g. Fairfield, Victoria") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(desc, { desc = it.take(2000) }, label = { Text("Details") },
            placeholder = { Text("Size, materials, access, anything a contractor needs to price it.") },
            minLines = 4, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(min, { min = it }, label = { Text("Budget from $") }, singleLine = true,
                keyboardOptions = decimalKeys, modifier = Modifier.weight(1f))
            OutlinedTextField(max, { max = it }, label = { Text("Budget to $") }, singleLine = true,
                keyboardOptions = decimalKeys, modifier = Modifier.weight(1f))
        }
        Picker("How soon?", timing, TIMING, { timing = it }, Modifier.fillMaxWidth())
        DateField("Preferred start", date, { date = it }, Modifier.fillMaxWidth())
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
    var date by rememberSaveable { mutableStateOf(existing?.availableDate ?: job.preferDate.ifBlank { plusDays(3) }) }
    var window by rememberSaveable { mutableStateOf(existing?.window ?: WINDOWS.first()) }
    var note by rememberSaveable { mutableStateOf(existing?.message ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    FormScaffold(
        if (existing != null) "Edit your bid" else "Place a bid", "${job.ref} · ${job.title}",
        back, if (existing != null) "Update bid" else "Send bid", error,
        onSubmit = {
            val amt = amount.toDoubleOrNull()
            error = if (company.isBlank() || amt == null || amt <= 0 || date.isBlank())
                "Add your business name, a price and the date you can be there." else null
            if (error == null) {
                vm.placeBid(job, company, amt!!, days.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0, date, window, note)
                back()
            }
        },
    ) {
        Text("Budget ${budgetText(job)} · ${job.area}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(company, { company = it.take(60) }, label = { Text("Business name") },
            placeholder = { Text("e.g. Coastline Plumbing Ltd.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(amount, { amount = it }, label = { Text("Price $ (before GST)") }, singleLine = true,
                keyboardOptions = decimalKeys, modifier = Modifier.weight(1.4f))
            OutlinedTextField(days, { days = it }, label = { Text("Days of work") }, singleLine = true,
                keyboardOptions = decimalKeys, modifier = Modifier.weight(1f))
        }
        DateField("Date you can be there", date, { date = it }, Modifier.fillMaxWidth())
        Picker("Arrival window", window, WINDOWS, { window = it }, Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Message to homeowner") },
            placeholder = { Text("What's included, licences, warranty.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
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
        lines.forEachIndexed { i, d ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RefText("Line ${i + 1}", Modifier.weight(1f))
                        if (lines.size > 1) IconButton(onClick = { lines.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove line ${i + 1}")
                        }
                    }
                    OutlinedTextField(d.desc, { lines[i] = d.copy(desc = it.take(120)) }, label = { Text("Description") },
                        placeholder = { Text(if (i == 0) "Materials" else "Labour") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(d.qty, { lines[i] = d.copy(qty = it) }, label = { Text("Qty / hrs") }, singleLine = true,
                            keyboardOptions = decimalKeys, modifier = Modifier.weight(1f))
                        OutlinedTextField(d.rate, { lines[i] = d.copy(rate = it) }, label = { Text("Rate $") }, singleLine = true,
                            keyboardOptions = decimalKeys, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        OutlinedButton(onClick = { lines.add(LineDraft()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add line")
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) { Totals(parsed.sumOf { it.total }) }
        }
        OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Notes") },
            placeholder = { Text("Scope, exclusions, warranty.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
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
    var error by remember { mutableStateOf<String?>(null) }

    FormScaffold("Add a receipt", job.ref, back, "Add receipt", error, onSubmit = {
        val amt = amount.toDoubleOrNull()
        error = if (vendor.isBlank() || amt == null || amt <= 0) "Add the vendor and the amount." else null
        if (error == null) {
            vm.addReceipt(job, vendor, amt!!, date, note)
            back()
        }
    }) {
        OutlinedTextField(vendor, { vendor = it.take(60) }, label = { Text("Vendor") },
            placeholder = { Text("e.g. RONA, Andrew Sheret") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount $ (incl. tax)") }, singleLine = true,
            keyboardOptions = decimalKeys, modifier = Modifier.fillMaxWidth())
        DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(120) }, label = { Text("What was it for?") },
            placeholder = { Text("e.g. 2 shut-off valves, braided lines") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
