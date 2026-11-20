@file:OptIn(ExperimentalMaterial3Api::class)

package app.workbid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

sealed interface Screen {
    data object Tabs : Screen
    data object PostJob : Screen
    data class Detail(val id: String) : Screen
    data class BidForm(val id: String) : Screen
    data class WorkOrderForm(val id: String) : Screen
    data class ReceiptForm(val id: String) : Screen
}

@Composable
fun WorkbidApp(vm: AppViewModel = viewModel()) {
    if (vm.uid == null) AuthScreen(vm) else MainScreen(vm)
}

// ---------------- sign in ----------------
@Composable
fun AuthScreen(vm: AppViewModel) {
    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val ready = email.isNotBlank() && password.length >= 6 && (!signUp || name.isNotBlank())

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        LogoMark(56.dp)
        Text("Workbid", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Post home jobs, get bids from local contractors, and track the work through to done.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (signUp) {
            OutlinedTextField(
                name, { name = it.take(60) }, label = { Text("Your name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            supportingText = { if (signUp) Text("At least 6 characters") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { if (signUp) vm.signUp(name, email, password) else vm.signIn(email, password) },
            enabled = ready && !vm.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (signUp) "Create account" else "Sign in")
        }
        TextButton(onClick = { signUp = !signUp; vm.message = null }, modifier = Modifier.fillMaxWidth()) {
            Text(if (signUp) "Have an account? Sign in" else "New here? Create an account")
        }
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------- signed-in shell ----------------
@Composable
fun MainScreen(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        vm.message = null
    }

    val back: () -> Unit = {
        screen = when (val s = screen) {
            is Screen.BidForm -> Screen.Detail(s.id)
            is Screen.WorkOrderForm -> Screen.Detail(s.id)
            is Screen.ReceiptForm -> Screen.Detail(s.id)
            else -> Screen.Tabs
        }
    }
    BackHandler(enabled = screen != Screen.Tabs, onBack = back)

    when (val s = screen) {
        Screen.Tabs -> TabsScreen(vm, snackbar, open = { screen = Screen.Detail(it) }, post = { screen = Screen.PostJob })
        Screen.PostJob -> PostJobForm(vm, back)
        is Screen.Detail -> JobDetailScreen(vm, s.id, snackbar, back) { screen = it }
        is Screen.BidForm -> BidFormScreen(vm, s.id, back)
        is Screen.WorkOrderForm -> WorkOrderFormScreen(vm, s.id, back)
        is Screen.ReceiptForm -> ReceiptFormScreen(vm, s.id, back)
    }
}

private data class TabSpec(val key: String, val label: String, val icon: ImageVector)

@Composable
fun TabsScreen(vm: AppViewModel, snackbar: SnackbarHostState, open: (String) -> Unit, post: () -> Unit) {
    val home = vm.isHomeowner
    val tabs = if (home) listOf(
        TabSpec("jobs", "My jobs", Icons.Default.Home),
        TabSpec("schedule", "Schedule", Icons.Default.DateRange),
    ) else listOf(
        TabSpec("find", "Find work", Icons.Default.Search),
        TabSpec("bids", "My bids", Icons.Default.List),
        TabSpec("schedule", "Schedule", Icons.Default.DateRange),
    )
    var tab by rememberSaveable(vm.role) { mutableStateOf(tabs.first().key) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LogoMark(34.dp)
                            Text("Workbid", fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.AccountCircle, contentDescription = "Account") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(vm.myName.ifBlank { "Signed in" }) }, onClick = {}, enabled = false)
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; vm.signOut() })
                        }
                    },
                )
                Row(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("I'm a", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    FilterChip(selected = home, onClick = { vm.setRole("home") }, label = { Text("Homeowner") })
                    FilterChip(selected = !home, onClick = { vm.setRole("pro") }, label = { Text("Contractor") })
                }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    val n = tabCount(vm, t.key)
                    NavigationBarItem(
                        selected = tab == t.key,
                        onClick = { tab = t.key },
                        icon = {
                            BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) {
                                Icon(t.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (home && tab == "jobs") {
                ExtendedFloatingActionButton(
                    onClick = post,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Post a job") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                "jobs" -> MyJobsTab(vm, open, post)
                "find" -> FindWorkTab(vm, open)
                "bids" -> MyBidsTab(vm, open) { tab = "find" }
                else -> ScheduleTab(vm, open)
            }
        }
    }
}

private fun tabCount(vm: AppViewModel, key: String): Int = when (key) {
    "jobs" -> vm.jobs.count { it.postedBy == vm.uid && it.status != "complete" }
    "find" -> vm.jobs.count { it.status == "open" && it.postedBy != vm.uid && vm.myBid(it.id) == null }
    "bids" -> vm.bids.count { b -> b.contractorId == vm.uid && vm.job(b.jobId)?.status == "open" }
    else -> scheduledJobs(vm).size
}

private fun scheduledJobs(vm: AppViewModel) = vm.jobs.filter {
    it.scheduledDate.isNotBlank() && it.status != "complete" &&
        (if (vm.isHomeowner) it.postedBy == vm.uid else it.contractorId == vm.uid)
}.sortedBy { it.scheduledDate }

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)

@Composable
fun MyJobsTab(vm: AppViewModel, open: (String) -> Unit, post: () -> Unit) {
    val mine = vm.jobs.filter { it.postedBy == vm.uid }.sortedByDescending { it.createdAt }
    val active = mine.filter { it.status != "complete" }
    val done = mine.filter { it.status == "complete" }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("My jobs", "Post what needs doing, compare bids, and track the work to done.") }
        if (mine.isEmpty()) item {
            EmptyState(
                "No jobs posted yet",
                "Describe the job, your area and budget. Contractors send bids with a price and the date they can be there.",
            ) { Button(onClick = post) { Text("Post a job") } }
        }
        items(active, key = { it.id }) { j -> JobCard(j, vm.bidsFor(j.id).size, onClick = { open(j.id) }) }
        if (done.isNotEmpty()) item {
            Text("COMPLETED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(done, key = { it.id }) { j -> JobCard(j, vm.bidsFor(j.id).size, onClick = { open(j.id) }) }
    }
}

@Composable
fun FindWorkTab(vm: AppViewModel, open: (String) -> Unit) {
    val openJobs = vm.jobs.filter { it.status == "open" }.sortedByDescending { it.createdAt }
    val cats = listOf("All") + CATS.filter { c -> openJobs.any { it.category == c } }
    var cat by rememberSaveable { mutableStateOf("All") }
    if (cat !in cats) cat = "All"
    val shown = openJobs.filter { cat == "All" || it.category == cat }

    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Find work", "Open jobs from homeowners. Send a price and the date you can be there.") }
        if (openJobs.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
            }
        }
        if (shown.isEmpty()) item { EmptyState("No open jobs right now", "New jobs appear here as homeowners post them.") }
        items(shown, key = { it.id }) { j ->
            val mine = vm.myBid(j.id)
            JobCard(
                j, vm.bidsFor(j.id).size, onClick = { open(j.id) },
                tag = when {
                    mine != null -> "You bid ${money(mine.amount)}"
                    j.postedBy == vm.uid -> "Your job"
                    else -> null
                },
            )
        }
    }
}

@Composable
fun MyBidsTab(vm: AppViewModel, open: (String) -> Unit, browse: () -> Unit) {
    val mine = vm.bids.filter { it.contractorId == vm.uid }.sortedByDescending { it.createdAt }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("My bids", "Every quote you've sent and where it stands.") }
        if (mine.isEmpty()) item {
            EmptyState("No bids yet", "Open a job in Find work and send your price, start date and arrival window.") {
                Button(onClick = browse) { Text("Browse open jobs") }
            }
        }
        items(mine, key = { it.id }) { b ->
            val j = vm.job(b.jobId) ?: return@items
            val (key, label) = when {
                j.status == "open" -> "pending" to "Pending"
                j.acceptedBidId == b.id -> "scheduled" to "Accepted"
                else -> "lost" to "Not selected"
            }
            JobCard(j, vm.bidsFor(j.id).size, onClick = { open(j.id) }, tag = "$label · ${money(b.amount)}", tagStatus = key)
        }
    }
}

@Composable
fun ScheduleTab(vm: AppViewModel, open: (String) -> Unit) {
    val list = scheduledJobs(vm)
    val todayIso = today()
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                "Schedule",
                if (vm.isHomeowner) "When your contractors are booked to arrive." else "Where you need to be, and when.",
            )
        }
        if (list.isEmpty()) item {
            EmptyState(
                "Nothing booked",
                if (vm.isHomeowner) "Accept a bid and the visit shows up here with its date and arrival window."
                else "Jobs you win show up here with the confirmed date and arrival window.",
            )
        }
        items(list, key = { it.id }) { j ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateBlock(j.scheduledDate, past = j.scheduledDate < todayIso)
                Box(Modifier.weight(1f)) { JobCard(j, 0, onClick = { open(j.id) }) }
            }
        }
    }
}

@Composable
fun DateBlock(iso: String, past: Boolean) {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (past) cs.surfaceVariant else cs.onBackground,
        contentColor = if (past) cs.onSurfaceVariant else cs.background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.width(60.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.month.name.take(3), style = MaterialTheme.typography.labelSmall)
            Text("${d.dayOfMonth}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(d.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall)
        }
    }
}
