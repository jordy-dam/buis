package nl.jordy.iptv.ui.theme

import androidx.compose.foundation.layout.PaddingValues
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
/** A focused row in a list: one step lighter than [SurfaceRaised], no accent (that's for "selected"). */
val SurfaceFocus = Color(0xFF2F3740)
val TextPrimary = Color(0xFFF2F4F5)
val TextSecondary = Color(0xFFA9B3BC)

/** TV overscan safe area. */
val SafeArea = 48.dp

/** One vertical rhythm for every screen of rows, so nothing reads as big and loose. */
object Rows {
    /** Between sections (a header plus its row). */
    val sectionGap = 16.dp
    /** Around a row of tiles: enough room for the 1.1 focus scale, no more. */
    val tilePadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val tileGap = 16.dp
}

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

// 10-foot type, below the spec's 16sp body at Jordy's request (2e, 2f). 13sp is the floor:
// smaller is unreadable from the couch.
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
