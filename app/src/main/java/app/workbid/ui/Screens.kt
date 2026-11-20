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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

sealed interface Screen {
    data object Tabs : Screen
    data object PostJob : Screen
    data object Account : Screen
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
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var startRole by rememberSaveable { mutableStateOf("home") }
    val focus = LocalFocusManager.current
    val emailOk = email.trim().let { it.contains("@") && it.substringAfter("@").contains(".") }
    val ready = emailOk && password.length >= 6 && (!signUp || name.isNotBlank())
    val submit = {
        focus.clearFocus()
        if (signUp) vm.signUp(name, email, password, startRole) else vm.signIn(email, password)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        LogoMark(60.dp)
        Text("Workbid", style = MaterialTheme.typography.displaySmall)
        Text(
            if (signUp) "Create a free account. You can post jobs and bid on work with the same account."
            else "Post home jobs, get bids from local contractors, and track the work through to done.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))

        if (signUp) {
            Text("What brings you here?", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = startRole == "home", onClick = { startRole = "home" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("I need work done") }
                SegmentedButton(
                    selected = startRole == "pro", onClick = { startRole = "pro" },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("I'm a contractor") }
            }
            OutlinedTextField(
                name, { name = it.take(60) }, label = { Text("Your name") }, singleLine = true,
                supportingText = { Text("Shown on your jobs and bids") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            email, { email = it.trim().take(120) }, label = { Text("Email") }, singleLine = true,
            isError = email.isNotBlank() && !emailOk && password.isNotEmpty(),
            supportingText = if (email.isNotBlank() && !emailOk && password.isNotEmpty()) ({ Text("Enter a full email, like name@example.com") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it.take(128) }, label = { Text("Password") }, singleLine = true,
            supportingText = { if (signUp) Text("At least 6 characters") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ready && !vm.busy) submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        vm.message?.let {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        Button(
            onClick = submit,
            enabled = ready && !vm.busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (signUp) "Create account" else "Sign in")
        }
        if (!signUp) {
            TextButton(
                onClick = { vm.sendPasswordReset(email) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Forgot password?") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        OutlinedButton(
            onClick = { signUp = !signUp; vm.message = null },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(if (signUp) "I already have an account" else "New here? Create an account")
        }
    }
}

// ---------------- signed-in shell ----------------
@Composable
fun MainScreen(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m, withDismissAction = m.length > 40)
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
        Screen.Tabs -> TabsScreen(
            vm, snackbar,
            open = { screen = Screen.Detail(it) },
            post = { screen = Screen.PostJob },
            account = { screen = Screen.Account },
        )
        Screen.PostJob -> PostJobForm(vm, back)
        Screen.Account -> AccountScreen(vm, snackbar, back)
        is Screen.Detail -> JobDetailScreen(vm, s.id, snackbar, back) { screen = it }
        is Screen.BidForm -> BidFormScreen(vm, s.id, back)
        is Screen.WorkOrderForm -> WorkOrderFormScreen(vm, s.id, back)
        is Screen.ReceiptForm -> ReceiptFormScreen(vm, s.id, back)
    }
}

private data class TabSpec(val key: String, val label: String, val icon: ImageVector)

@Composable
fun TabsScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    open: (String) -> Unit,
    post: () -> Unit,
    account: () -> Unit,
) {
    val home = vm.isHomeowner
    val tabs = if (home) listOf(
        TabSpec("jobs", "My jobs", Icons.Default.Home),
        TabSpec("schedule", "Schedule", Icons.Default.DateRange),
    ) else listOf(
        TabSpec("find", "Find work", Icons.Default.Search),
        TabSpec("bids", "My bids", Icons.AutoMirrored.Filled.List),
        TabSpec("schedule", "Schedule", Icons.Default.DateRange),
    )
    var tab by rememberSaveable(vm.role) { mutableStateOf(tabs.first().key) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogoMark(34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Workbid", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = account) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Account and settings", Modifier.size(28.dp))
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                    SegmentedButton(
                        selected = home, onClick = { vm.setRole("home") },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { SegmentedButtonDefaults.Icon(home) { Icon(Icons.Default.Home, null, Modifier.size(18.dp)) } },
                    ) { Text("Homeowner") }
                    SegmentedButton(
                        selected = !home, onClick = { vm.setRole("pro") },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { SegmentedButtonDefaults.Icon(!home) { Icon(Icons.Default.Build, null, Modifier.size(18.dp)) } },
                    ) { Text("Contractor") }
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
    "jobs" -> vm.jobs.count { it.postedBy == vm.uid && it.status == "open" && vm.bidsFor(it.id).isNotEmpty() }
    "find" -> vm.jobs.count { it.status == "open" && it.postedBy != vm.uid && vm.myBid(it.id) == null }
    "bids" -> vm.bids.count { b -> b.contractorId == vm.uid && vm.job(b.jobId)?.status == "open" }
    else -> scheduledJobs(vm).count { it.scheduledDate <= today() }
}

private fun scheduledJobs(vm: AppViewModel) = vm.jobs.filter {
    it.scheduledDate.isNotBlank() && it.status != "complete" &&
        (if (vm.isHomeowner) it.postedBy == vm.uid else it.contractorId == vm.uid)
}.sortedBy { it.scheduledDate }

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp)

@Composable
private fun WelcomeCard(vm: AppViewModel) {
    if (!vm.justSignedUp) return
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Welcome, ${vm.myName.substringBefore(" ")}!", style = MaterialTheme.typography.titleLarge)
            if (vm.isHomeowner) {
                Steps(listOf(
                    "Post a job: what needs doing, your area and budget.",
                    "Contractors send bids with a price and the date they can come.",
                    "Accept the bid you like. The visit lands in your Schedule.",
                ))
            } else {
                Steps(listOf(
                    "Browse open jobs in Find work.",
                    "Send a price, how long it takes, and when you can be there.",
                    "When a homeowner accepts, the job lands in your Schedule.",
                ))
            }
            Text(
                "Switch between Homeowner and Contractor any time at the top.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = { vm.justSignedUp = false }, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
        }
    }
}

@Composable
fun Steps(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MyJobsTab(vm: AppViewModel, open: (String) -> Unit, post: () -> Unit) {
    val mine = vm.jobs.filter { it.postedBy == vm.uid }.sortedByDescending { it.createdAt }
    val needsAction = mine.filter { it.status == "open" && vm.bidsFor(it.id).isNotEmpty() }
    val waiting = mine.filter { it.status == "open" && vm.bidsFor(it.id).isEmpty() }
    val booked = mine.filter { it.status == "scheduled" || it.status == "in_progress" }
    val done = mine.filter { it.status == "complete" }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("My jobs", "Post what needs doing, compare bids, and track the work to done.") }
        item { WelcomeCard(vm) }
        if (!vm.loaded) { item { LoadingCards() }; return@LazyColumn }
        if (mine.isEmpty()) item {
            EmptyState(
                "No jobs posted yet",
                "Describe the job, your area and budget. Contractors send bids with a price and the date they can be there.",
                icon = Icons.Default.Home,
            ) { Button(onClick = post) { Text("Post your first job") } }
        }
        if (needsAction.isNotEmpty()) item { GroupLabel("Bids to review") }
        items(needsAction, key = { it.id }) { j ->
            val n = vm.bidsFor(j.id).size
            JobCard(j, n, onClick = { open(j.id) }, tag = "$n bid${if (n == 1) "" else "s"} · review", tagStatus = "open")
        }
        if (waiting.isNotEmpty()) item { GroupLabel("Waiting for bids") }
        items(waiting, key = { it.id }) { j -> JobCard(j, 0, onClick = { open(j.id) }) }
        if (booked.isNotEmpty()) item { GroupLabel("Booked") }
        items(booked, key = { it.id }) { j -> JobCard(j, 0, onClick = { open(j.id) }) }
        if (done.isNotEmpty()) item { GroupLabel("Completed") }
        items(done, key = { it.id }) { j -> JobCard(j, 0, onClick = { open(j.id) }) }
    }
}

@Composable
fun FindWorkTab(vm: AppViewModel, open: (String) -> Unit) {
    val openJobs = vm.jobs.filter { it.status == "open" && it.postedBy != vm.uid }.sortedByDescending { it.createdAt }
    val cats = listOf("All") + CATS.filter { c -> openJobs.any { it.category == c } }
    var cat by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    if (cat !in cats) cat = "All"
    val q = query.trim().lowercase()
    val shown = openJobs.filter { j ->
        (cat == "All" || j.category == cat) &&
            (q.isEmpty() || j.title.lowercase().contains(q) || j.area.lowercase().contains(q) ||
                j.description.lowercase().contains(q))
    }
    val focus = LocalFocusManager.current

    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Find work", "Open jobs from homeowners. Send a price and the date you can be there.") }
        item { WelcomeCard(vm) }
        if (!vm.loaded) { item { LoadingCards() }; return@LazyColumn }
        if (openJobs.isNotEmpty()) {
            item {
                OutlinedTextField(
                    query, { query = it.take(60) },
                    placeholder = { Text("Search by job or area") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                }
            }
        }
        if (openJobs.isEmpty()) item {
            EmptyState(
                "No open jobs right now",
                "New jobs appear here the moment homeowners post them. Check back soon.",
                icon = Icons.Default.Search,
            )
        } else if (shown.isEmpty()) item {
            EmptyState("No matches", "Try a different word or trade.", icon = Icons.Default.Search) {
                TextButton(onClick = { query = ""; cat = "All" }) { Text("Clear filters") }
            }
        }
        items(shown, key = { it.id }) { j ->
            val mine = vm.myBid(j.id)
            JobCard(
                j, vm.bidsFor(j.id).size, onClick = { open(j.id) },
                tag = mine?.let { "You bid ${money(it.amount)}" },
                tagStatus = "scheduled",
            )
        }
    }
}

@Composable
fun MyBidsTab(vm: AppViewModel, open: (String) -> Unit, browse: () -> Unit) {
    val mine = vm.bids.filter { it.contractorId == vm.uid }.sortedByDescending { it.createdAt }
    val withJobs = mine.mapNotNull { b -> vm.job(b.jobId)?.let { b to it } }
    val pending = withJobs.filter { (_, j) -> j.status == "open" }
    val won = withJobs.filter { (b, j) -> j.status != "open" && j.acceptedBidId == b.id }
    val lost = withJobs.filter { (b, j) -> j.status != "open" && j.acceptedBidId != b.id }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("My bids", "Every quote you've sent and where it stands.") }
        if (!vm.loaded) { item { LoadingCards() }; return@LazyColumn }
        if (withJobs.isEmpty()) item {
            EmptyState(
                "No bids yet",
                "Open a job in Find work and send your price, start date and arrival window.",
                icon = Icons.AutoMirrored.Filled.Send,
            ) { Button(onClick = browse) { Text("Browse open jobs") } }
        }
        if (won.isNotEmpty()) item { GroupLabel("Won") }
        items(won, key = { it.first.id }) { (b, j) ->
            JobCard(j, 0, onClick = { open(j.id) }, tag = "Accepted · ${money(b.amount)}", tagStatus = "complete")
        }
        if (pending.isNotEmpty()) item { GroupLabel("Waiting on homeowner") }
        items(pending, key = { it.first.id }) { (b, j) ->
            JobCard(j, vm.bidsFor(j.id).size, onClick = { open(j.id) }, tag = "Pending · ${money(b.amount)}", tagStatus = "open")
        }
        if (lost.isNotEmpty()) item { GroupLabel("Not selected") }
        items(lost, key = { it.first.id }) { (b, j) ->
            JobCard(j, 0, onClick = { open(j.id) }, tag = "Not selected · ${money(b.amount)}", tagStatus = "lost")
        }
    }
}

@Composable
fun ScheduleTab(vm: AppViewModel, open: (String) -> Unit) {
    val list = scheduledJobs(vm)
    val todayDate = LocalDate.now()
    val todayIso = todayDate.toString()
    val weekEnd = todayDate.plusDays(7).toString()
    val groups = listOf(
        "Past due" to list.filter { it.scheduledDate < todayIso },
        "Today" to list.filter { it.scheduledDate == todayIso },
        "Next 7 days" to list.filter { it.scheduledDate > todayIso && it.scheduledDate <= weekEnd },
        "Later" to list.filter { it.scheduledDate > weekEnd },
    ).filter { it.second.isNotEmpty() }

    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                "Schedule",
                if (vm.isHomeowner) "When your contractors are booked to arrive." else "Where you need to be, and when.",
            )
        }
        if (!vm.loaded) { item { LoadingCards(2) }; return@LazyColumn }
        if (list.isEmpty()) item {
            EmptyState(
                "Nothing booked",
                if (vm.isHomeowner) "Accept a bid and the visit shows up here with its date and arrival window."
                else "Jobs you win show up here with the confirmed date and arrival window.",
                icon = Icons.Default.DateRange,
            )
        }
        groups.forEach { (label, jobs) ->
            item(key = "label-$label") { GroupLabel(label) }
            items(jobs, key = { it.id }) { j ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateBlock(j.scheduledDate, past = j.scheduledDate < todayIso, today = j.scheduledDate == todayIso)
                    Box(Modifier.weight(1f)) { JobCard(j, 0, onClick = { open(j.id) }) }
                }
            }
        }
    }
}

@Composable
fun DateBlock(iso: String, past: Boolean, today: Boolean = false) {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return
    val cs = MaterialTheme.colorScheme
    Surface(
        color = when {
            today -> cs.secondary
            past -> cs.errorContainer
            else -> cs.onBackground
        },
        contentColor = when {
            today -> cs.onSecondary
            past -> cs.onErrorContainer
            else -> cs.background
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.width(60.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.month.name.take(3), style = MaterialTheme.typography.labelSmall)
            Text("${d.dayOfMonth}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(d.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall)
        }
    }
}
