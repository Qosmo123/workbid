@file:OptIn(ExperimentalMaterial3Api::class)

package app.workbid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.workbid.data.Message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA)
private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.CANADA)

/** Messages between the homeowner and the contractor booked on one job. */
@Composable
fun ChatScreen(vm: AppViewModel, jobId: String, snackbar: SnackbarHostState, back: () -> Unit) {
    val job = vm.job(jobId)
    if (job == null || job.contractorId.isBlank()) {
        LaunchedEffect(Unit) { back() }
        return
    }
    val isOwner = job.postedBy == vm.uid
    val other = if (isOwner) job.company.ifBlank { job.contractorName } else job.postedByName.ifBlank { "Homeowner" }
    val msgs = vm.messagesFor(job.id)
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    // Opening the chat (and each new message while it's open) marks everything as read.
    LaunchedEffect(msgs.size) {
        vm.markRead(job.id)
        if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Column {
                        Text(other, style = MaterialTheme.typography.titleMedium)
                        RefText("${job.ref} · ${job.title}")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        draft, { draft = it.take(1000) },
                        placeholder = { Text("Message $other") },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = {
                            sending = true
                            // If sending fails, the error shows in the snackbar and the draft stays.
                            vm.sendMessage(job, draft) { ok -> if (ok) draft = ""; sending = false }
                        },
                        enabled = draft.isNotBlank() && !sending,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    "Only you and $other can read these messages. Agree on extra work with a work order, so it's in writing with a price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (msgs.isEmpty()) item {
                EmptyState(
                    "No messages yet",
                    if (isOwner) "Say hello, and share anything they need to know: parking, access, pets, the gate code."
                    else "Say hello, confirm the time, and ask anything you need to know before you arrive.",
                )
            }
            var lastDay: LocalDate? = null
            msgs.forEach { m ->
                val day = Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                if (day != lastDay) {
                    val label = when (day) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> day.format(DAY)
                    }
                    item(key = "day-$day") {
                        Text(
                            label, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    lastDay = day
                }
                item(key = m.id) { Bubble(m, mine = m.by == vm.uid) }
            }
        }
    }
}

@Composable
private fun Bubble(m: Message, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault()).format(TIME),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
