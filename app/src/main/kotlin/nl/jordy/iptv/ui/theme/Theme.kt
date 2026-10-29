package nl.jordy.iptv.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.LocalContentColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/*
 * Neutral greys with three colours that each mean one thing (2g): the logo's blue for buttons and
 * what's selected, amber for live ("NU", progress, reminders), red for the heart. Focus is not a
 * colour but a step in lightness: the focused row turns [FocusFill]. [onLight] covers the few light
 * surfaces ([FocusBar] key hints).
 */
val Background = Color(0xFF131416)
val SurfaceColor = Color(0xFF1C1D21)
val SurfaceRaised = Color(0xFF26282D)
/** The logo's blue, a touch lighter so it holds up on the dark background. */
val Brand = Color(0xFF2D6AA8)
val Heart = Color(0xFFE5484D)
val FocusBar = Color(0xFFECEBE7)
/** Focus on rows, chips and buttons: one clear step lighter than a row, text stays light (2g, after the white bar). */
val FocusFill = Color(0xFF3B3E46)
/** The logo blue as text: values in Instellingen, readable on the dark background. */
val BrandText = Color(0xFF8DB8E8)
val TextPrimary = Color(0xFFF3F3F1)
val TextOnLight = Color(0xFF131416)

/** True inside the focus bar: the surface switched its content colour to dark. */
@Composable
fun onLight(): Boolean = LocalContentColor.current.luminance() < 0.5f

/** Secondary text that stays readable on the focus bar too. */
val TextSecondary: Color
    @Composable get() = if (onLight()) Color(0xFF5C5F66) else Color(0xFFA3A6AD)

/** Amber for live things; a dark amber on the focus bar, where the light one disappears. */
val Live: Color
    @Composable get() = if (onLight()) Color(0xFF8A5A00) else Color(0xFFF2B544)

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
    primary = Brand,
    onPrimary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Color(0xFFA3A6AD),
    inverseSurface = FocusBar,
    inverseOnSurface = TextOnLight,
    border = FocusBar,
)

// 10-foot type, below the spec's 16sp body at Jordy's request (2e, 2f, and one more step in 2g as a
// tv test). 12sp is now the floor: below that is unreadable from the couch.
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
