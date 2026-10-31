package nl.jordy.iptv.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
 *
 * A profile picks its [Look]; every colour below reads the active [Palette], so screens never ask.
 */
enum class Look(val label: String) {
    STANDARD("Standaard"),
    /** Night garage: wet concrete, cold strip lights, one hard red and a little gold. */
    GARAGE("Garage");

    companion object {
        fun of(index: Int): Look = entries.getOrElse(index) { STANDARD }
    }
}

class Palette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val brand: Color,
    val brandText: Color,
    val heart: Color,
    /** The heart on the light focus fill. */
    val heartOnLight: Color,
    /** The heart icon inside a [heart]-filled badge. */
    val onHeart: Color,
    val focusBar: Color,
    val focusFill: Color,
    val textPrimary: Color,
    val textOnLight: Color,
    val textSecondary: Color,
    val textSecondaryOnLight: Color,
    val live: Color,
    val liveOnLight: Color,
)

private val standard = Palette(
    background = Color(0xFF131416),
    surface = Color(0xFF1C1D21),
    surfaceRaised = Color(0xFF26282D),
    // The logo's blue, a touch lighter so it holds up on the dark background.
    brand = Color(0xFF2D6AA8),
    brandText = Color(0xFF8DB8E8),
    heart = Color(0xFFE5484D),
    heartOnLight = Color(0xFFE5484D),
    onHeart = Color(0xFFF3F3F1),
    focusBar = Color(0xFFECEBE7),
    focusFill = Color(0xFF3B3E46),
    textPrimary = Color(0xFFF3F3F1),
    textOnLight = Color(0xFF131416),
    textSecondary = Color(0xFFA3A6AD),
    textSecondaryOnLight = Color(0xFF5C5F66),
    live = Color(0xFFF2B544),
    liveOnLight = Color(0xFF8A5A00),
)

// Red is the accent here, so the heart is the rims' gold, like live; shape tells them apart (Jordy's
// pick, 2026-10-31: a white heart disappeared in its own white badge).
private val garage = Palette(
    background = Color(0xFF0D0F12),
    surface = Color(0xFF16191D),
    surfaceRaised = Color(0xFF20242A),
    brand = Color(0xFFB3262B),
    brandText = Color(0xFFEE8A8C),
    heart = Color(0xFFD4AE55),
    heartOnLight = Color(0xFF6E5210),
    onHeart = Color(0xFF0D0F12),
    focusBar = Color(0xFFE9EEF2),
    focusFill = Color(0xFF343A43),
    textPrimary = Color(0xFFF1F3F5),
    textOnLight = Color(0xFF0D0F12),
    textSecondary = Color(0xFF9AA1AA),
    textSecondaryOnLight = Color(0xFF59606A),
    live = Color(0xFFD4AE55),
    liveOnLight = Color(0xFF6E5210),
)

private fun Look.palette() = when (this) {
    Look.STANDARD -> standard
    Look.GARAGE -> garage
}

private val LocalPalette = staticCompositionLocalOf { standard }

val Background: Color @Composable get() = LocalPalette.current.background
val SurfaceColor: Color @Composable get() = LocalPalette.current.surface
val SurfaceRaised: Color @Composable get() = LocalPalette.current.surfaceRaised
/**
 * Everything that floats over the picture or a screen: the player bar, its track lists and every
 * pop-up (corner card). One surface, see-through so the picture shows a little; at 60 % text and
 * icons stay readable over the brightest colour bars. Focus on it is the light [FocusBar] fill, not
 * [FocusFill]: a grey step disappears into a translucent grey surface.
 */
val PopupSurface: Color @Composable get() = SurfaceRaised.copy(alpha = 0.60f)
/** The accent: buttons and what's selected. */
val Brand: Color @Composable get() = LocalPalette.current.brand
/** The heart; a darker shade on the focus bar, where a light one disappears. */
val Heart: Color @Composable get() = LocalPalette.current.let { if (onLight()) it.heartOnLight else it.heart }
/** The icon on a [Heart]-filled badge. */
val OnHeart: Color @Composable get() = LocalPalette.current.onHeart
val FocusBar: Color @Composable get() = LocalPalette.current.focusBar
/** Focus on rows, chips and buttons: one clear step lighter than a row, text stays light (2g, after the white bar). */
val FocusFill: Color @Composable get() = LocalPalette.current.focusFill
/** The accent as text: values in Instellingen, readable on the dark background. */
val BrandText: Color @Composable get() = LocalPalette.current.brandText
val TextPrimary: Color @Composable get() = LocalPalette.current.textPrimary
val TextOnLight: Color @Composable get() = LocalPalette.current.textOnLight

/** True inside the focus bar: the surface switched its content colour to dark. */
@Composable
fun onLight(): Boolean = LocalContentColor.current.luminance() < 0.5f

/** Secondary text that stays readable on the focus bar too. */
val TextSecondary: Color
    @Composable get() = LocalPalette.current.let { if (onLight()) it.textSecondaryOnLight else it.textSecondary }

/** Live things; a darker shade on the focus bar, where the light one disappears. */
val Live: Color
    @Composable get() = LocalPalette.current.let { if (onLight()) it.liveOnLight else it.live }

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

private fun Palette.colors() = darkColorScheme(
    primary = brand,
    onPrimary = textPrimary,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    inverseSurface = focusBar,
    inverseOnSurface = textOnLight,
    border = focusBar,
)

// 10-foot type, below the spec's 16sp body at Jordy's request (2e, 2f, and one more step in 2g as a
// tv test, kept). 12sp is the floor: below that is unreadable from the couch.
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
fun IptvTheme(look: Look = Look.STANDARD, content: @Composable () -> Unit) {
    val palette = look.palette()
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = palette.colors(), typography = typography, content = content)
    }
}
