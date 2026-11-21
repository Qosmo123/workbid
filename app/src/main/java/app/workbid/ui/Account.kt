@file:OptIn(ExperimentalMaterial3Api::class)

package app.workbid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.workbid.AppConfig
import app.workbid.BuildConfig

@Composable
fun AccountScreen(vm: AppViewModel, snackbar: SnackbarHostState, back: () -> Unit, editProfile: () -> Unit) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var howItWorks by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) } // 0 = closed, 1 = explain, 2 = password

    LaunchedEffect(Unit) { vm.refreshAccount() }

    fun openUrl(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    fun email(to: String, subject: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to?subject=" + Uri.encode(subject))))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Account") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- profile ----
            Section("Profile") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(vm.myName.ifBlank { "No name set" }, style = MaterialTheme.typography.titleMedium)
                        Text(vm.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { editName = true }) { Text("Edit name") }
                }
                if (vm.emailVerified) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Email verified", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    NextStepCard(
                        "Verify your email",
                        "We sent a link to ${vm.email}. Tap it, then come back here. Check your spam folder if you can't find it.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.resendVerification() }) { Text("Send again") }
                        TextButton(onClick = { vm.refreshAccount() }) { Text("I've verified") }
                    }
                }
            }

            // ---- contractor profile ----
            Section("Contractor profile") {
                val p = vm.myProfile
                SettingRow(
                    Icons.Default.Build,
                    if (p == null) "Set up your contractor profile" else p.company.ifBlank { "Your contractor profile" },
                    if (p == null) "Trades, experience and insurance, shown with your bids"
                    else listOf(p.trades.joinToString(", "), profileBadges(p).joinToString(" · ")).filter { it.isNotBlank() }
                        .joinToString(" · ").ifBlank { "Tap to edit" },
                ) { editProfile() }
            }

            // ---- help ----
            Section("Help") {
                SettingRow(Icons.Default.Info, "How Workbid works", "A quick tour for homeowners and contractors") { howItWorks = true }
                SettingRow(Icons.Default.Lock, "Change password", "We'll email you a link to set a new one") {
                    vm.sendPasswordReset(vm.email)
                }
                if (AppConfig.SUPPORT_EMAIL.isNotBlank()) {
                    SettingRow(Icons.Default.Email, "Contact support", AppConfig.SUPPORT_EMAIL) {
                        email(AppConfig.SUPPORT_EMAIL, "Workbid help")
                    }
                }
            }

            // ---- blocked people ----
            Section("Blocked people") {
                if (vm.blocked.isEmpty()) {
                    Text(
                        "Nobody. To block someone, tap Block on their bid, or use the ⋮ menu on their job.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    vm.blocked.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(b.name.ifBlank { "Workbid user" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.unblock(b) }) { Text("Unblock") }
                        }
                    }
                    Text(
                        "Blocked people can't bid on your jobs, and you won't see their jobs or bids. They aren't told.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- legal ----
            if (AppConfig.PRIVACY_POLICY_URL.isNotBlank() || AppConfig.TERMS_URL.isNotBlank()) {
                Section("Legal") {
                    if (AppConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                        SettingRow(null, "Privacy policy", null) { openUrl(AppConfig.PRIVACY_POLICY_URL) }
                    }
                    if (AppConfig.TERMS_URL.isNotBlank()) {
                        SettingRow(null, "Terms of use", null) { openUrl(AppConfig.TERMS_URL) }
                    }
                }
            }

            // ---- account actions ----
            Section("Account") {
                SettingRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", null) { confirmSignOut = true }
                SettingRow(Icons.Default.Delete, "Delete account", "Permanently remove your account and data", danger = true) {
                    deleteStep = 1
                }
            }

            Text(
                "Workbid ${BuildConfig.VERSION_NAME}" + if (BuildConfig.USE_EMULATOR) " · test data (emulator)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    // ---- dialogs ----
    if (editName) {
        var name by rememberSaveable { mutableStateOf(vm.myName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shown on new jobs and bids. Ones you've already posted keep the old name.")
                    OutlinedTextField(name, { name = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.updateName(name); editName = false }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }

    if (howItWorks) {
        AlertDialog(
            onDismissRequest = { howItWorks = false },
            title = { Text("How Workbid works") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Homeowners", style = MaterialTheme.typography.titleSmall)
                    Steps(listOf(
                        "Post a job with your area, budget and preferred date.",
                        "Compare bids: price, days of work, and when they can come.",
                        "Accept one. The date is booked for both of you.",
                        "Approve work orders and log receipts as the job goes.",
                        "Mark it complete when the work is done.",
                    ))
                    Text("Contractors", style = MaterialTheme.typography.titleSmall)
                    Steps(listOf(
                        "Find open jobs, filtered by trade or searched by area.",
                        "Send your price, days of work, date and arrival window.",
                        "If you're accepted, the job lands in your Schedule.",
                        "Tap Start work on the day, add a work order for approval.",
                        "Mark it complete when you're done.",
                    ))
                    Text(
                        "One account does both. Use the switch at the top of the home screen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { howItWorks = false }) { Text("Close") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your jobs and bids stay saved. Sign back in any time with ${vm.email}.") },
            confirmButton = { Button(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }

    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This can't be undone. We'll remove:")
                    Text("• Your profile and sign-in\n• Jobs you posted that are still open, and their bids\n• Bids you sent that weren't booked")
                    Text(
                        "Booked and completed jobs stay, with their work orders and receipts, so the other person keeps their records.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { deleteStep = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Keep my account") } },
        )
    }

    if (deleteStep == 2) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!vm.busy) deleteStep = 0 },
            title = { Text("Confirm with your password") },
            text = {
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteAccount(password) { deleteStep = 0 } },
                    enabled = password.isNotEmpty() && !vm.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Delete forever")
                }
            },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }, enabled = !vm.busy) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
