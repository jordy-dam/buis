package nl.jordy.iptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** One accent colour on a near-black background. Everything focusable lights up in the accent. */
val Accent = Color(0xFF4FC3F7)
val Background = Color(0xFF0E1114)
val SurfaceColor = Color(0xFF1A1F24)
val SurfaceRaised = Color(0xFF252B32)
val TextPrimary = Color(0xFFF2F4F5)
val TextSecondary = Color(0xFFA9B3BC)

/** TV overscan safe area. */
val SafeArea = 48.dp

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00222F),
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    border = Accent,
)

// 10-foot type: body never below 16sp.
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
