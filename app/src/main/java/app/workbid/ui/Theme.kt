package app.workbid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Blueprint teal for actions, tape-measure yellow for highlights, cool workshop neutrals.
private val Light = lightColorScheme(
    primary = Color(0xFF0D6670),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCECEC),
    onPrimaryContainer = Color(0xFF0D3A40),
    secondary = Color(0xFFE6B22A),
    onSecondary = Color(0xFF2B2200),
    secondaryContainer = Color(0xFFFBF0CF),
    onSecondaryContainer = Color(0xFF2B2200),
    background = Color(0xFFEEF0EC),
    onBackground = Color(0xFF1B2429),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2429),
    surfaceVariant = Color(0xFFF5F6F3),
    onSurfaceVariant = Color(0xFF5B676D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9F6),
    surfaceContainer = Color(0xFFF5F6F3),
    surfaceContainerHigh = Color(0xFFEFF1ED),
    surfaceContainerHighest = Color(0xFFE8EBE6),
    outline = Color(0xFFB9C0BA),
    outlineVariant = Color(0xFFD8DDD7),
    error = Color(0xFFA83A2C),
    onError = Color(0xFFFFFFFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF52B3BA),
    onPrimary = Color(0xFF0A1517),
    primaryContainer = Color(0xFF17343A),
    onPrimaryContainer = Color(0xFFBFE6E8),
    secondary = Color(0xFFE6B22A),
    onSecondary = Color(0xFF241C00),
    secondaryContainer = Color(0xFF3A3214),
    onSecondaryContainer = Color(0xFFF6E3A8),
    background = Color(0xFF111618),
    onBackground = Color(0xFFE7ECE9),
    surface = Color(0xFF192024),
    onSurface = Color(0xFFE7ECE9),
    surfaceVariant = Color(0xFF1F272B),
    onSurfaceVariant = Color(0xFF97A3A7),
    surfaceContainerLowest = Color(0xFF0D1113),
    surfaceContainerLow = Color(0xFF151B1E),
    surfaceContainer = Color(0xFF192024),
    surfaceContainerHigh = Color(0xFF1F272B),
    surfaceContainerHighest = Color(0xFF263034),
    outline = Color(0xFF3E4A50),
    outlineVariant = Color(0xFF2B353A),
    error = Color(0xFFEF8A7B),
    onError = Color(0xFF2A0C07),
)

// Bold, compact headings; comfortable body text for reading job details on a phone.
private val base = Typography()
private val WorkbidType = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

private val WorkbidShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WorkbidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = WorkbidType,
        shapes = WorkbidShapes,
        content = content,
    )
}

/** Background and text colour for a job status pill. */
@Composable
fun statusColors(status: String): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return when (status) {
        "open" -> cs.secondaryContainer to cs.onSecondaryContainer
        "scheduled" -> cs.primaryContainer to cs.primary
        "in_progress" -> if (dark) Color(0xFF3A2A17) to Color(0xFFE2A157) else Color(0xFFF6E6D2) to Color(0xFFA8641A)
        "complete" -> if (dark) Color(0xFF173226) to Color(0xFF6CC58A) else Color(0xFFDDEFE2) to Color(0xFF2E7A48)
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}
