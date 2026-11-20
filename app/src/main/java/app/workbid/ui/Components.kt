@file:OptIn(ExperimentalMaterial3Api::class)

package app.workbid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun MoneyText(v: Double, big: Boolean = false) {
    Text(
        money(v),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (big) 20.sp else 15.sp,
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
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
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
    bidCount: Int,
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
                    if (job.status == "open") "Budget ${budgetText(job)}" else job.company,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    tag != null -> {
                        val (bg, fg) = statusColors(tagStatus)
                        Pill(tag, bg, fg)
                    }
                    job.status == "open" -> Text(
                        "$bidCount bid${if (bidCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> MoneyText(job.price)
                }
            }
        }
    }
}

// ---------- form fields ----------
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
