package nl.jordy.iptv.ui.components

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
import nl.jordy.iptv.ui.theme.SurfaceFocus
import androidx.compose.ui.graphics.Shape
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
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import nl.jordy.iptv.data.settings.FilteredCategories
import nl.jordy.iptv.ui.theme.Accent
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary

private val tileShape = RoundedCornerShape(10.dp)

/**
 * Two focus treatments. Artwork (posters, avatars, episodes): scale ~1.1, accent outline, soft
 * glow, because one tile is picked out of a wall of images. Rows in lists: [quiet], a lighter
 * surface and a thin light line, so focus never looks like the accent used for "selected" and progress.
 */
object Focus {
    const val SCALE = 1.1f
    val border = Border(BorderStroke(3.dp, Accent), shape = tileShape)
    val glow = Glow(elevationColor = Accent.copy(alpha = 0.35f), elevation = 12.dp)
    fun quiet(shape: Shape) = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), shape = shape)
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
        glow = CardDefaults.glow(focusedGlow = Focus.glow),
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
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(42.dp).onMenuKey(onLongClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Focus.quiet(RoundedCornerShape(8.dp))),
        colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceColor, focusedContainerColor = SurfaceFocus),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Favourite first, in a fixed slot, so numbers and names line up with or without it.
                Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    if (favorite) Text("♥︎", color = Accent, style = MaterialTheme.typography.labelLarge)
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
                Text(
                    now?.let { "NU · ${it.title}" } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.58f).padding(start = 12.dp),
                )
                // Always the same width, empty or not: the "NU" text must never shift between rows.
                Row(Modifier.width(72.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (catchup) Text("↺︎", color = TextSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 6.dp))
                    if (quality != null) QualityLabel(quality)
                }
            }
            if (now != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(now.progress()).height(2.dp)
                        .background(Accent.copy(alpha = 0.45f)),
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
        border = ClickableSurfaceDefaults.border(focusedBorder = Focus.quiet(RoundedCornerShape(8.dp))),
        colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceColor, focusedContainerColor = SurfaceFocus),
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
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(now.progress()).height(2.dp).background(Accent.copy(alpha = 0.45f)))
            }
        }
    }
}

/** A quiet outlined label for picture quality, so it reads as a property, not a button. */
@Composable
private fun QualityLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        maxLines = 1,
        modifier = Modifier.border(1.dp, TextSecondary.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** The best picture quality among a channel's labels, or null; "Multi" and the like aren't qualities. */
fun bestQuality(badges: List<String>): String? {
    val rank = listOf("UHD", "4K", "FHD", "HD")
    return badges.map { it.uppercase() }.filter { it in rank }.minByOrNull { rank.indexOf(it) }
        ?.let { if (it == "UHD") "4K" else it }
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
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Focus.quiet(RoundedCornerShape(8.dp))),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = SurfaceFocus,
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).background(Accent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text("♥︎", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, thickness: Dp = 5.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(SurfaceRaised)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(thickness).background(Accent))
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
