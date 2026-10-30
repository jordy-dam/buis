package nl.jordy.iptv.ui.components

import androidx.compose.material.icons.filled.Check
import nl.jordy.iptv.ui.theme.Background
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import nl.jordy.iptv.data.xtream.EpgEntry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import nl.jordy.iptv.data.settings.FilteredCategories
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.Heart
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.FocusFill
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary

private val tileShape = RoundedCornerShape(10.dp)

/**
 * Two focus treatments (2g). Artwork (posters, episodes): scale ~1.1 and a thin light outline, as
 * there's no surface to fill. Everything else (rows, chips, buttons, the rail): [bar], the row steps
 * up to [FocusFill], no border. A white bar was tried first and was too bright from the couch.
 */
object Focus {
    const val SCALE = 1.1f
    val border = Border(BorderStroke(2.dp, FocusBar), shape = tileShape)

    @Composable
    fun bar(container: Color = SurfaceColor) = ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = TextPrimary,
        focusedContainerColor = FocusFill,
        focusedContentColor = TextPrimary,
        pressedContainerColor = FocusFill,
        pressedContentColor = TextPrimary,
    )
}

/**
 * Menu key = secondary action (favourite), alongside long-press on OK. Some remotes have no
 * menu key, so long-press must always work too.
 */
fun Modifier.onMenuKey(action: () -> Unit) = onPreviewKeyEvent {
    if (it.type == KeyEventType.KeyUp && it.key.nativeKeyCode == KeyEvent.KEYCODE_MENU) {
        action(); true
    } else false
}

/**
 * For the last item of a horizontal row: Right stays put instead of jumping to whatever is nearest
 * in another row (e.g. from the last season up to the Favoriet button). Left is untouched, so the
 * first item can still reach the navigation rail.
 */
fun Modifier.lastInRow(isLast: Boolean) = if (isLast) focusProperties { right = FocusRequester.Cancel } else this

/** 2:3 poster for films and series. The title shows through when the artwork is missing or broken. */
@Composable
fun PosterTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 92.dp,
    favorite: Boolean = false,
    progress: Float? = null,
    /** A small note in the corner, such as "2 versies". */
    label: String? = null,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(width).aspectRatio(2f / 3f).onMenuKey(onLongClick),
        shape = CardDefaults.shape(tileShape),
        scale = CardDefaults.scale(focusedScale = Focus.SCALE),
        border = CardDefaults.border(focusedBorder = Focus.border),
        colors = CardDefaults.colors(containerColor = SurfaceColor),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Fallback when there's no artwork (or it's broken): looks like a title card, not an error.
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (favorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * One-line channel row, 48dp, so about eleven fit on screen: number, logo plate (logos are often
 * transparent PNGs), name, what's on now, labels, and a thin progress bar along the bottom.
 * Fixed height on purpose: a row that grows on focus makes the whole list jump.
 */
@Composable
fun ChannelTile(
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    now: EpgEntry? = null,
    favorite: Boolean = false,
    /** Best picture quality ("4K", "FHD"…), see [bestQuality]. */
    quality: String? = null,
    catchup: Boolean = false,
    /** The row's own background; over the picture it's see-through, so only its box shades it. */
    container: Color = SurfaceColor,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(42.dp).onMenuKey(onLongClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = Focus.bar(container),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Favourite first, in a fixed slot, so numbers and names line up with or without it.
                Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    if (favorite) androidx.tv.material3.Icon(Icons.Filled.Favorite, contentDescription = "Favoriet", tint = Heart, modifier = Modifier.size(16.dp))
                }
                if (number != null) {
                    Text("$number", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.height(28.dp).aspectRatio(16f / 9f).background(SurfaceRaised, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text(name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    if (logoUrl != null) {
                        AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.42f),
                )
                // Same columns on every row, guide or not, so nothing moves when a programme loads.
                val live = Live
                Text(
                    now?.let { buildAnnotatedString { withStyle(SpanStyle(color = live, fontWeight = FontWeight.Medium)) { append("NU") }; append(" · ${it.title}") } }
                        ?: AnnotatedString(""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.58f).padding(start = 12.dp),
                )
                // Always the same width, empty or not: the "NU" text must never shift between rows.
                Row(Modifier.width(72.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    // Terugkijken: the programme can be watched from its start.
                    if (catchup) androidx.tv.material3.Icon(Icons.Filled.History, contentDescription = "Terugkijken", tint = TextSecondary, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                    if (quality != null) TagLabel(quality)
                }
            }
            if (now != null) {
                // A hint of how far the programme is, not a second highlight: a list of full amber
                // lines outshouts the names.
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(now.progress()).height(1.dp)
                        .background(Live.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

/**
 * A favourite channel on Home: 240×60, logo, name and what's on, so three or four fit in a row.
 */
@Composable
fun ChannelCard(name: String, logoUrl: String?, now: EpgEntry?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(240.dp).height(60.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = Focus.bar(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(32.dp).aspectRatio(16f / 9f).background(SurfaceRaised, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text(name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    if (logoUrl != null) {
                        AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(now?.title ?: " ", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (now != null) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(now.progress()).height(1.dp).background(Live.copy(alpha = 0.45f)))
            }
        }
    }
}

/** A quiet label for picture quality or a tag like "PPV", so it reads as a property, not a button. */
@Composable
fun TagLabel(text: String) {
    // Drawn like Material's HD / SD / 4K icons (letters in a block). Better is lighter, not a colour:
    // colours each mean one thing in BUIS (blue selected, amber live, red heart), and most channels are
    // HD, so a coloured badge would tint whole lists. SD is only an outline.
    val shape = RoundedCornerShape(3.dp)
    val fill = when (text) {
        "4K", "8K" -> TextPrimary
        "SD" -> null
        else -> TextSecondary
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = if (fill == null) TextSecondary else Background,
        maxLines = 1,
        modifier = (if (fill == null) Modifier.border(1.dp, TextSecondary, shape) else Modifier.background(fill, shape))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** The best picture quality among a channel's labels, or null; "Multi" and the like aren't qualities. */
fun bestQuality(badges: List<String>): String? {
    val rank = listOf("8K", "UHD", "4K", "FHD", "HD", "SD")
    return badges.map { it.uppercase() }.filter { it in rank }.minByOrNull { rank.indexOf(it) }
        ?.let { if (it == "UHD") "4K" else it }
}

/**
 * The labels a category shows next to its name: its best quality, then tags like "PPV" or "Multi".
 * Frame rates stay out. Codecs stay in: "BEIN SPORTS" with HEVC and with RAW are different categories.
 */
fun categoryTags(badges: String): List<String> {
    val all = badges.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val technical = Regex("""\d+FPS|8K|UHD|4K|FHD|HD|SD""", RegexOption.IGNORE_CASE)
    return listOfNotNull(bestQuality(all)) + all.filterNot { technical.matches(it) }
}

/** How far the programme is, 0..1, at this moment. */
fun EpgEntry.progress(nowSec: Long = System.currentTimeMillis() / 1000): Float =
    if (stop <= start) 0f else ((nowSec - start).toFloat() / (stop - start)).coerceIn(0f, 1f)

/**
 * What's on [streamId] now and next, loaded once the row has settled on screen and refreshed
 * every minute, so titles roll over and progress bars move.
 */
@Composable
fun rememberNowAndNext(streamId: Int, load: suspend (Int) -> Pair<EpgEntry?, EpgEntry?>): Pair<EpgEntry?, EpgEntry?> {
    val value by produceState<Pair<EpgEntry?, EpgEntry?>>(null to null, streamId) {
        delay(150) // only for rows that stay on screen while scrolling
        while (true) {
            value = load(streamId)
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    return value
}

/** Plain focusable row for lists such as categories. */
@Composable
fun ListItemSurface(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Multi-select lists: a check in front of picked items (and room for it in front of the others). */
    checked: Boolean? = null,
    /** Labels after the name ("PPV", "FHD"), see [categoryTags]. */
    tags: List<String> = emptyList(),
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().then(if (onLongClick != null) Modifier.onMenuKey(onLongClick) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = Focus.bar(container = if (selected) Brand.copy(alpha = 0.35f) else Color.Transparent),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (checked != null) {
                Box(Modifier.padding(end = 10.dp).size(20.dp)) {
                    if (checked) androidx.tv.material3.Icon(Icons.Filled.Check, contentDescription = "Gekozen", modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Medium else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            tags.forEach { Spacer(Modifier.width(8.dp)); TagLabel(it) }
        }
    }
}

@Composable
fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).background(Heart, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        androidx.tv.material3.Icon(Icons.Filled.Favorite, contentDescription = "Favoriet", tint = TextPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, thickness: Dp = 5.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(SurfaceRaised)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(thickness).background(Live))
    }
}

/** Last entry of a category list: switches the country filter off for this visit, or back on. */
fun LazyListScope.filterToggle(filter: FilteredCategories?, onToggle: () -> Unit) {
    val f = filter ?: return
    if (f.hidden == 0 && !f.showingAll) return
    item(key = "filter-toggle") {
        ListItemSurface(
            text = if (f.showingAll) "Alleen voorkeurslanden" else "Alle landen tonen (${f.hidden} meer)",
            selected = false,
            onClick = onToggle,
        )
    }
}
