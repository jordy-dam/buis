package nl.jordy.iptv.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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

// 10-foot type, one step below the spec's 16sp body at Jordy's request (2e): secondary text is 14sp.
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/** The BUIS wordmark: plain condensed bold, matching the banner (docs/brand/make-logo.sh). */
@Composable
fun BuisWordmark(modifier: Modifier = Modifier) {
    Text(
        "BUIS",
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)),
            letterSpacing = 1.sp,
            color = TextPrimary,
        ),
        modifier = modifier,
    )
}
