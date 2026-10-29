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
import nl.jordy.iptv.data.db.FavoriteEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.PlaybackPositionEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.repo.EpgRepository
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
    val channels: List<FavoriteEntity> = emptyList(),
    val recent: List<MovieEntity> = emptyList(),
) {
    val isEmpty get() = continueWatching.isEmpty() && channels.isEmpty() && recent.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val db: AppDatabase,
    private val library: LibraryRepository,
    profiles: ProfileRepository,
    private val epg: EpgRepository,
    follows: FollowRepository,
) : ViewModel() {
    /** "Straks voor jou": what the profile follows, refreshed every minute and whenever a term changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val upcoming = follows.terms.flatMapLatest {
        flow {
            while (true) {
                emit(follows.upcoming().take(ROW_SIZE))
                delay(60_000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun nowAndNext(streamId: Int) = epg.nowAndNext(streamId)

    fun removeFromContinue(c: ContinueItem) = viewModelScope.launch { library.forgetPosition(c.type, c.itemId) }

    private val continueWatching = library.recentPositions(ROW_SIZE).map { positions -> positions.mapNotNull { resolve(it) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recent = profiles.prefs.flatMapLatest { p ->
        db.catalog().recentMovies(p.preferredCountries.toList(), p.hideAdult, ROW_SIZE)
    }

    val state = combine(continueWatching, library.favorites(), recent) { cw, favs, rec ->
        HomeState(cw, favs.filter { it.type == ContentType.LIVE }, rec)
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

/** The watching profile's start screen: where they left off, their channels, and what's new. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(onPlay: (PlayerRoute) -> Unit, onOpenMovie: (Int) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val s = loaded ?: return
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
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
                    "Hier komt te staan waar je gebleven was en je favoriete zenders. Begin bij Live TV of Films.",
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
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                Text("Straks voor jou", style = MaterialTheme.typography.titleLarge)
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
        if (s.channels.isNotEmpty()) {
            item(key = "channels") {
                Text("Favoriete zenders", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    contentPadding = Rows.tilePadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(s.channels, key = { it.itemId }) { f ->
                        ChannelCard(
                            name = NameParser.parse(f.name).title,
                            logoUrl = f.imageUrl,
                            now = rememberNowAndNext(f.itemId, vm::nowAndNext).first,
                            // Zapping from here goes through the favourite channels, as on Favorieten.
                            onClick = { onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, f.itemId, f.name, categoryId = PlayerRoute.FAVORITES_CATEGORY)) },
                            modifier = Modifier.lastInRow(f == s.channels.last()),
                        )
                    }
                }
            }
        }
        if (s.recent.isNotEmpty()) {
            item(key = "recent") {
                Text("Recent toegevoegd", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    contentPadding = Rows.tilePadding,
                    horizontalArrangement = Arrangement.spacedBy(Rows.tileGap),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(s.recent, key = { it.streamId }) { m ->
                        PosterTile(
                            title = m.name,
                            imageUrl = m.posterUrl,
                            onClick = { onOpenMovie(m.streamId) },
                            onLongClick = {},
                            modifier = Modifier.lastInRow(m == s.recent.last()),
                        )
                    }
                }
            }
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
private fun whenLabel(startSec: Long): String {
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
