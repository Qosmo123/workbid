@file:OptIn(ExperimentalMaterial3Api::class)

package app.workbid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.workbid.data.HomeJob
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- constants & formatting ----------
val CATS = listOf(
    "Plumbing", "Electrical", "Carpentry", "Painting", "Roofing", "HVAC",
    "Flooring", "Landscaping", "Appliances", "General repair",
)
val WINDOWS = listOf("8–10 AM", "10 AM–12 PM", "12–2 PM", "2–4 PM", "4–6 PM", "Flexible")
val TIMING = listOf("Flexible", "Within 2 weeks", "This week", "Urgent (24–48 h)")
const val GST = 0.05
// Must match reportReasons() in firestore.rules.
val REPORT_REASONS = listOf(
    "Spam or scam", "Offensive or abusive", "Fake or misleading", "Contact details or personal info", "Something else",
)

private val CAD: NumberFormat = NumberFormat.getCurrencyInstance(Locale.CANADA)
fun money(v: Double): String = CAD.format(v)
fun today(): String = LocalDate.now().toString()
fun plusDays(n: Long): String = LocalDate.now().plusDays(n).toString()

fun fdate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.CANADA))
}.getOrDefault("—")

fun ago(t: Long): String {
    if (t <= 0) return ""
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 2 -> "just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        else -> "${m / (60 * 24)} d ago"
    }
}

fun statusLabel(s: String) = when (s) {
    "open" -> "Open for bids"
    "scheduled" -> "Scheduled"
    "in_progress" -> "In progress"
    "complete" -> "Complete"
    else -> s
}

fun budgetText(j: HomeJob) =
    if (j.budgetMin <= 0 && j.budgetMax <= 0) "Open budget" else "${money(j.budgetMin)}–${money(j.budgetMax)}"

// ---------- small pieces ----------
@Composable
fun LogoMark(box: Dp = 44.dp) {
    val roof = MaterialTheme.colorScheme.secondary
    val wall = MaterialTheme.colorScheme.background
    Box(
        Modifier.size(box).clip(RoundedCornerShape(box / 5)).background(MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(box * 0.62f)) {
            val w = size.width
            val h = size.height
            drawPath(
                Path().apply { moveTo(0f, h * 0.5f); lineTo(w / 2, h * 0.12f); lineTo(w, h * 0.5f) },
                roof, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                Path().apply { moveTo(w * 0.2f, h * 0.46f); lineTo(w * 0.2f, h * 0.94f); lineTo(w * 0.8f, h * 0.94f); lineTo(w * 0.8f, h * 0.46f) },
                wall, style = Stroke(width = w * 0.1f, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(5.dp)) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun StatusPill(status: String) {
    val (bg, fg) = statusColors(status)
    Pill(statusLabel(status), bg, fg)
}

@Composable
fun RefText(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}

/** Body text with tabular (evenly spaced) digits, for money. */
@Composable
fun moneyStyle() = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

@Composable
fun MoneyText(v: Double, big: Boolean = false) {
    // Tabular figures keep digits evenly spaced without a typewriter look.
    Text(
        money(v),
        fontWeight = FontWeight.Bold,
        fontSize = if (big) 21.sp else 15.sp,
        style = moneyStyle(),
    )
}

@Composable
fun ScreenHeader(title: String, sub: String) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, Modifier.size(28.dp)) }
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

/** A tinted card that tells people what happens next, so nobody is left guessing. */
@Composable
fun NextStepCard(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, Modifier.padding(top = 2.dp).size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Grey placeholder cards shown while the first data arrives, instead of a misleading empty state. */
@Composable
fun LoadingCards(count: Int = 3) {
    val shimmer = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            OutlinedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                    Box(Modifier.fillMaxWidth(0.8f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                    Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                }
            }
        }
    }
}

/** Uppercase label above a group of list items. */
@Composable
fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun Section(title: String, onAdd: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onAdd != null) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun JobCard(
    job: HomeJob,
    /** Shown only to the job's owner; pass null to hide it (other people's bids are private). */
    bidCount: Int?,
    onClick: () -> Unit,
    tag: String? = null,
    tagStatus: String = "pending",
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefText("${job.ref} · ${job.category}", Modifier.weight(1f))
                StatusPill(job.status)
            }
            Text(job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val whenText = if (job.scheduledDate.isNotBlank()) "${fdate(job.scheduledDate)} · ${job.window}" else job.timing
            Text(
                listOf(job.area, whenText, "Posted ${ago(job.createdAt)}").filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (job.status != "open") job.company else if (job.budgetMin <= 0 && job.budgetMax <= 0) "Open budget" else "Budget ${budgetText(job)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    tag != null -> {
                        val (bg, fg) = statusColors(tagStatus)
                        Pill(tag, bg, fg)
                    }
                    job.status == "open" && bidCount != null -> Text(
                        "$bidCount bid${if (bidCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    job.status != "open" -> MoneyText(job.price)
                }
            }
        }
    }
}

// ---------- form fields ----------
val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

/** Text field with a "$" prefix, number keyboard, and an inline error under it. */
@Composable
fun MoneyField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    help: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(10)) },
        label = { Text(label) },
        prefix = { Text("$") },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: help)?.let { { Text(it) } },
        keyboardOptions = decimalKeys,
        modifier = modifier,
    )
}

/** Single-line text field with an inline error and an optional character counter. */
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    max: Int,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    minLines: Int = 1,
    showCount: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            showCount -> ({ Text("${value.length} / $max", Modifier.fillMaxWidth(), textAlign = TextAlign.End) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (minLines == 1) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
@Composable
fun Picker(label: String, value: String, options: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false })
            }
        }
    }
}

private fun isoToUtcMillis(iso: String): Long? = runCatching {
    LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

@Composable
fun DateField(label: String, value: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else fdate(value), onValueChange = {}, readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Pick a date") },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
