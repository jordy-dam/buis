package nl.jordy.iptv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.PlaybackPositionEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.Guide
import nl.jordy.iptv.data.repo.GuideRepository
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.ui.components.rememberNowAndNext
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelCard
import nl.jordy.iptv.ui.components.PosterTile
import nl.jordy.iptv.ui.components.lastInRow
import nl.jordy.iptv.ui.theme.Rows
import nl.jordy.iptv.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.ignoreHeldKey
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import nl.jordy.iptv.data.repo.FollowMatch
import nl.jordy.iptv.data.repo.FollowMatches
import nl.jordy.iptv.data.repo.FollowRepository
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.theme.Live
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.theme.SurfaceColor
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Icon
import nl.jordy.iptv.data.repo.Taste
import nl.jordy.iptv.data.repo.TasteRepository
import javax.inject.Inject

/** A "Verder kijken" tile: a film or an episode, ready to resume. */
data class ContinueItem(
    val key: String,
    val title: String,
    val imageUrl: String?,
    val progress: Float,
    val remainingMs: Long,
    val play: PlayerRoute,
    /** Which saved position this is, so it can be removed from the row. */
    val type: ContentType,
    val itemId: Int,
)

data class HomeState(
    val continueWatching: List<ContinueItem> = emptyList(),
    /** The profile's guides that have channels, one row each. */
    val guides: List<Guide> = emptyList(),
) {
    val isEmpty get() = continueWatching.isEmpty() && guides.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val db: AppDatabase,
    private val library: LibraryRepository,
    guides: GuideRepository,
    private val epg: EpgRepository,
    follows: FollowRepository,
    taste: TasteRepository,
) : ViewModel() {
    /** "Films voor jou": films that share genre, director or actors with what the profile liked. */
    val forYou = taste.suggestions(ContentType.MOVIE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** "Series voor jou": the same taste, over the series. */
    val seriesForYou = taste.suggestions(ContentType.SERIES).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Wat je volgt": what the profile follows, refreshed every minute and whenever a term changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val upcoming = follows.terms.flatMapLatest {
        flow {
            while (true) {
                emit(FollowMatches.nextPerTerm(follows.upcoming()).take(ROW_SIZE))
                delay(60_000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun nowAndNext(streamId: Int) = epg.nowAndNext(streamId)

    fun removeFromContinue(c: ContinueItem) = viewModelScope.launch { library.forgetPosition(c.type, c.itemId) }

    private val continueWatching = library.recentPositions(ROW_SIZE).map { positions -> positions.mapNotNull { resolve(it) } }

    val state = combine(continueWatching, guides.guides) { cw, g ->
        HomeState(cw, g.filter { it.channels.isNotEmpty() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null when the item is gone from the catalog, or an episode whose series was never opened here. */
    private suspend fun resolve(p: PlaybackPositionEntity): ContinueItem? {
        val progress = if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f
        val left = (p.durationMs - p.positionMs).coerceAtLeast(0)
        return when (p.type) {
            ContentType.MOVIE -> db.catalog().movie(p.itemId)?.let { m ->
                ContinueItem("m${m.streamId}", m.name, m.posterUrl, progress, left, PlayerRoute(PlayerRoute.Kind.MOVIE, m.streamId, m.name, m.containerExtension), p.type, p.itemId)
            }
            ContentType.SERIES -> {
                val e = db.episodes().episode(p.itemId) ?: return null
                val s = db.catalog().series(e.seriesId)
                val title = "${s?.name ?: e.title} · S${e.season} A${e.number}"
                ContinueItem("e${e.episodeId}", title, s?.coverUrl ?: e.imageUrl, progress, left, PlayerRoute(PlayerRoute.Kind.EPISODE, e.episodeId, title, e.containerExtension), p.type, p.itemId)
            }
            ContentType.LIVE -> null
        }
    }

    private companion object {
        const val ROW_SIZE = 20
    }
}

/** The watching profile's start screen, only what's personal: where they left off, what's coming up, a row per guide, films for them. Browsing (Nieuw too) is at Films. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(onPlay: (PlayerRoute) -> Unit, onOpenMovie: (Int) -> Unit, onOpenSeries: (Int) -> Unit, onOpenTaste: (series: Boolean) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val s = loaded ?: return
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val forYou by vm.forYou.collectAsStateWithLifecycle()
    val seriesForYou by vm.seriesForYou.collectAsStateWithLifecycle()
    var focusedContinue by remember { mutableStateOf<ContinueItem?>(null) }
    var confirmRemove by remember { mutableStateOf<ContinueItem?>(null) }
    // After a removal the tile is gone: focus moves to its neighbour, or it would be lost.
    val tileFocus = remember { mutableMapOf<String, FocusRequester>() }
    var refocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.continueWatching) {
        val key = refocus ?: return@LaunchedEffect
        refocus = null
        withFrameNanos { }
        runCatching { tileFocus[key]?.requestFocus() }
    }
    confirmRemove?.let { c ->
        RemoveDialog(
            title = c.title,
            onConfirm = {
                val list = s.continueWatching
                val i = list.indexOf(c)
                refocus = (list.getOrNull(i + 1) ?: list.getOrNull(i - 1))?.key
                vm.removeFromContinue(c)
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(Rows.sectionGap)) {
        if (s.isEmpty) {
            item {
                Text(
                    "Hier komt te staan waar je gebleven was en de zenders uit je gids. Begin bij Live TV of Films; nieuwe films staan bij Films onder Uitgelicht.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
        }
        if (s.continueWatching.isNotEmpty()) {
            item(key = "continue") {
                Text("Verder kijken", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    contentPadding = Rows.tilePadding,
                    horizontalArrangement = Arrangement.spacedBy(Rows.tileGap),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(s.continueWatching, key = { it.key }) { c ->
                        PosterTile(
                            title = c.title,
                            imageUrl = c.imageUrl,
                            progress = c.progress,
                            onClick = { onPlay(c.play) },
                            onLongClick = { confirmRemove = c },
                            modifier = Modifier.lastInRow(c == s.continueWatching.last())
                                .focusRequester(tileFocus.getOrPut(c.key) { FocusRequester() })
                                .onFocusChanged { if (it.isFocused) focusedContinue = c else if (focusedContinue == c) focusedContinue = null },
                        )
                    }
                }
                // What the focused tile is, even when its artwork has no title. Always one line high, so nothing jumps.
                Text(
                    focusedContinue?.let { "${it.title} · nog ${remaining(it.remainingMs)}" } ?: " ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, top = 14.dp),
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                Text("Wat je volgt", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    contentPadding = Rows.tilePadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(upcoming, key = { it.key }) { m ->
                        UpcomingCard(m, onClick = {
                            onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, m.streamId, m.channelName, categoryId = m.categoryId))
                        }, modifier = Modifier.lastInRow(m == upcoming.last()))
                    }
                }
            }
        }
        s.guides.forEach { guide ->
            item(key = "guide/${guide.id}") {
                // The guide's own name ("Sport") says little on its own: say it's one of yours.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(guide.name, style = MaterialTheme.typography.titleLarge)
                    Text("jouw gids", style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
                }
                LazyRow(
                    contentPadding = Rows.tilePadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(guide.channels, key = { it.streamId }) { c ->
                        ChannelCard(
                            name = NameParser.parse(c.name).title,
                            logoUrl = c.logoUrl,
                            now = rememberNowAndNext(c.streamId, vm::nowAndNext).first,
                            // Zapping from here goes through this guide, as on Favorieten.
                            onClick = { onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, c.streamId, c.name, categoryId = PlayerRoute.guideCategory(guide.id))) },
                            modifier = Modifier.lastInRow(c == guide.channels.last()),
                        )
                    }
                }
            }
        }
        item(key = "foryou") {
            TasteRow("Films voor jou", forYou, onLearn = { onOpenTaste(false) }, onOpen = { onOpenMovie(it.streamId) })
        }
        item(key = "seriesforyou") {
            TasteRow("Series voor jou", seriesForYou, onLearn = { onOpenTaste(true) }, onOpen = { onOpenSeries(it.streamId) }, series = true)
        }
    }
}

/** A followed programme: when and where on top, the title below. OK tunes in. */
@Composable
private fun UpcomingCard(m: FollowMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(260.dp).height(64.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = Focus.bar(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            val live = Live
            Text(
                buildAnnotatedString {
                    val started = m.start <= System.currentTimeMillis() / 1000
                    if (started) withStyle(SpanStyle(color = live, fontWeight = FontWeight.Medium)) { append("NU") } else append(whenLabel(m.start))
                    append(" · ${m.channelName}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "15:00" today, "Morgen 15:00", else "Za 15:00". */
internal fun whenLabel(startSec: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochSecond(startSec).atZone(zone)
    val today = LocalDate.now(zone)
    val time = "%02d:%02d".format(at.hour, at.minute)
    return when (at.toLocalDate()) {
        today -> time
        today.plusDays(1) -> "Morgen $time"
        else -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("nl-NL")).replaceFirstChar { it.uppercase() } + " $time"
    }
}

/** Long-press on a "Verder kijken" tile. Focus starts on Verwijderen: it's what the long-press was for. */
@Composable
private fun RemoveDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val remove = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Uit Verder kijken halen?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "$title verdwijnt uit deze rij. Kijk je hem later toch, dan begint hij vooraan.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton("Verwijderen", onConfirm, Modifier.focusRequester(remove))
                SecondaryButton("Annuleren", onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { remove.requestFocus() }
}

/** "1 u 56 min", "12 min", "1 min". */
private fun remaining(ms: Long): String {
    val min = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    return if (min >= 60) "${min / 60} u ${min % 60} min" else "$min min"
}

/** First in "Voor jou": opens "Leer mijn smaak". The size of a poster, so the row stays one height. */
@Composable
private fun LearnTasteTile(first: Boolean, series: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier.width(92.dp).aspectRatio(2f / 3f),
        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = Focus.SCALE),
        border = androidx.tv.material3.CardDefaults.border(focusedBorder = Focus.border),
        colors = androidx.tv.material3.CardDefaults.colors(containerColor = SurfaceColor),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = Live, modifier = Modifier.size(26.dp))
            Text(
                if (first) "Leer mijn smaak" else if (series) "Meer series beoordelen" else "Meer films beoordelen",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}


/**
 * "Films voor jou" or "Series voor jou": "Leer mijn smaak" first, then the suggestions, and under
 * the row why the focused one is there. The line is always one line high, so nothing jumps.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TasteRow(title: String, items: List<Taste.Suggestion>, onLearn: () -> Unit, onOpen: (Taste.Film) -> Unit, series: Boolean = false) {
    var focused by remember { mutableStateOf<Taste.Suggestion?>(null) }
    Text(title, style = MaterialTheme.typography.titleLarge)
    LazyRow(
        contentPadding = Rows.tilePadding,
        horizontalArrangement = Arrangement.spacedBy(Rows.tileGap),
        modifier = Modifier.focusRestorer(),
    ) {
        item(key = "learn") {
            LearnTasteTile(
                first = items.isEmpty(),
                series = series,
                onClick = onLearn,
                modifier = Modifier.lastInRow(items.isEmpty()).onFocusChanged { if (it.isFocused) focused = null },
            )
        }
        items(items, key = { it.film.key }) { f ->
            PosterTile(
                title = f.film.title,
                imageUrl = f.film.posterUrl,
                onClick = { onOpen(f.film) },
                onLongClick = {},
                modifier = Modifier.lastInRow(f == items.last())
                    .onFocusChanged { if (it.isFocused) focused = f else if (focused == f) focused = null },
            )
        }
    }
    Text(
        focused?.let { f ->
            listOfNotNull(
                Taste.displayTitle(f.film.title, f.film.year),
                Taste.ratingLabel(f.film.rating),
                f.because?.let { "omdat je ${Taste.displayTitle(it)} leuk vond" },
            ).joinToString(" · ")
        }
            ?: if (items.isEmpty()) "Zeg van tien ${if (series) "series" else "films"} of je ze leuk vindt, dan zoekt BUIS er ${if (series) "series" else "films"} bij." else " ",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp),
    )
}
