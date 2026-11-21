@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.workbid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.workbid.data.Profile

/** Contractors describe themselves here. Homeowners see it from each bid and booking. */
@Composable
fun ProfileFormScreen(vm: AppViewModel, back: () -> Unit) {
    val p = vm.myProfile
    var company by rememberSaveable { mutableStateOf(p?.company?.ifBlank { null } ?: vm.lastCompany()) }
    var trades by rememberSaveable { mutableStateOf(p?.trades ?: emptyList()) }
    var area by rememberSaveable { mutableStateOf(p?.serviceArea ?: "") }
    var years by rememberSaveable { mutableStateOf(p?.years?.takeIf { it > 0 }?.toString() ?: "") }
    var licence by rememberSaveable { mutableStateOf(p?.licence ?: "") }
    var insured by rememberSaveable { mutableStateOf(p?.insured ?: false) }
    var about by rememberSaveable { mutableStateOf(p?.about ?: "") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val yearsNum = years.toIntOrNull()
    val companyErr = if (tried && company.isBlank()) "Add your business name" else null
    val tradesErr = if (tried && trades.isEmpty()) "Pick at least one" else null
    val yearsErr = if (years.isNotBlank() && (yearsNum == null || yearsNum !in 0..80)) "0 to 80" else null
    val valid = company.isNotBlank() && trades.isNotEmpty() && yearsErr == null

    FormScaffold(
        "Contractor profile", "Shown to homeowners with your bids", back, "Save profile",
        if (tried && !valid) "Fix the highlighted fields above." else null,
        onSubmit = {
            tried = true
            if (valid) vm.saveProfile(company, about, trades, area, yearsNum ?: 0, licence, insured, back)
        },
    ) {
        NextStepCard(
            "Why fill this in?",
            "Homeowners pick contractors they trust. A complete profile with your trades, experience and insurance " +
                "helps your bids stand out from a price alone.",
        )
        FormField(company, { company = it }, "Business name", 60, placeholder = "e.g. Coastline Plumbing Ltd.", error = companyErr)

        Text("Trades you do", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CATS.forEach { c ->
                val on = c in trades
                FilterChip(
                    selected = on,
                    onClick = { trades = if (on) trades - c else trades + c },
                    label = { Text(c) },
                    leadingIcon = if (on) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
                )
            }
        }
        if (tradesErr != null) Text(tradesErr, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        FormField(area, { area = it }, "Area you serve", 60, placeholder = "e.g. Greater Victoria")
        OutlinedTextField(
            years, { v -> years = v.filter { it.isDigit() }.take(2) },
            label = { Text("Years in the trade") }, singleLine = true, isError = yearsErr != null,
            supportingText = yearsErr?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            licence, { licence = it }, "Licence or certification (optional)", 80,
            placeholder = "e.g. Red Seal plumber, Technical Safety BC licence #",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("I carry liability insurance", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Only switch this on if it's true. Homeowners may ask for your certificate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = insured, onCheckedChange = { insured = it })
        }
        FormField(
            about, { about = it }, "About your business", 500, minLines = 4, showCount = true,
            placeholder = "What you specialise in, warranty on your work, how you like to communicate.",
        )
        Text(
            "Don't include your phone number or email here. Homeowners can message you in the app once you're booked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small facts from a profile, shown on bid cards: "Insured · Licensed · 12 yrs". Empty if there's no profile. */
fun profileBadges(p: Profile?): List<String> {
    if (p == null) return emptyList()
    return listOfNotNull(
        "Insured".takeIf { p.insured },
        "Licensed".takeIf { p.licence.isNotBlank() },
        p.years.takeIf { it > 0 }?.let { "$it yr${if (it == 1) "" else "s"} experience" },
    )
}

/** A contractor's profile in a dialog. */
@Composable
fun ProfileDialog(vm: AppViewModel, uid: String, fallbackName: String, onDismiss: () -> Unit) {
    LaunchedEffect(uid) { vm.loadProfile(uid) }
    val loaded = vm.profiles.containsKey(uid)
    val p = vm.profiles[uid]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p?.company?.ifBlank { null } ?: fallbackName) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    p == null -> Text("This contractor hasn't filled in a profile yet. Ask them about their experience and insurance in your messages.")
                    else -> {
                        if (p.trades.isNotEmpty()) ProfileLine("Trades", p.trades.joinToString(", "))
                        if (p.serviceArea.isNotBlank()) ProfileLine("Serves", p.serviceArea)
                        if (p.years > 0) ProfileLine("Experience", "${p.years} year${if (p.years == 1) "" else "s"}")
                        if (p.licence.isNotBlank()) ProfileLine("Licence", p.licence)
                        ProfileLine("Insurance", if (p.insured) "Says they carry liability insurance" else "Not stated")
                        if (p.about.isNotBlank()) Text(p.about, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "Contractors fill this in themselves and Workbid doesn't check it. Ask to see their licence " +
                                "and insurance certificate before work starts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
