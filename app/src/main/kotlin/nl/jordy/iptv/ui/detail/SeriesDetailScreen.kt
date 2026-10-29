package nl.jordy.iptv.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.EpisodeEntity
import nl.jordy.iptv.data.db.PlaybackPositionEntity
import nl.jordy.iptv.data.db.SeriesEntity
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.SeriesInfo
import nl.jordy.iptv.player.ResumePolicy
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.SeriesDetailRoute
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.ListItemSurface
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.ProgressBar
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.lastInRow
import nl.jordy.iptv.ui.player.formatTime
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val library: LibraryRepository,
) : ViewModel() {
    val seriesId = savedState.toRoute<SeriesDetailRoute>().seriesId

    val series = MutableStateFlow<SeriesEntity?>(null)
    val info = MutableStateFlow<SeriesInfo?>(null)
    val loading = MutableStateFlow(true)
    val episodes = library.episodes(seriesId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val positions = library.positions(ContentType.SERIES).map { list -> list.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val isFavorite = library.isFavorite(ContentType.SERIES, seriesId).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { series.value = db.catalog().series(seriesId) }
        viewModelScope.launch {
            info.value = library.refreshSeries(seriesId)
            loading.value = false
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val s = series.value ?: return@launch
        library.toggleFavorite(ContentType.SERIES, s.seriesId, s.name, s.coverUrl)
    }
}

/** The episode to put behind the main button: the one most recently watched, else the first. */
internal fun nextUp(episodes: List<EpisodeEntity>, positions: Map<Int, PlaybackPositionEntity>): EpisodeEntity? =
    episodes.filter { positions[it.episodeId] != null }.maxByOrNull { positions.getValue(it.episodeId).updatedAt }
        ?: episodes.firstOrNull()

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailScreen(onPlay: (PlayerRoute) -> Unit, vm: SeriesDetailViewModel = hiltViewModel()) {
    val series by vm.series.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val s = series ?: return

    val seasons = episodes.map { it.season }.distinct().sorted()
    val next = nextUp(episodes, positions)
    var season by rememberSaveable { mutableStateOf<Int?>(null) }
    val shownSeason = season ?: next?.season ?: seasons.firstOrNull()
    val primary = remember { FocusRequester() }
    LaunchedEffect(next != null) { if (next != null) runCatching { primary.requestFocus() } }

    fun play(e: EpisodeEntity) = onPlay(PlayerRoute(PlayerRoute.Kind.EPISODE, e.episodeId, "${s.name} – ${e.title}", e.containerExtension))

    DetailScaffold(
        title = s.name,
        posterUrl = s.coverUrl,
        backdropUrl = info?.backdropUrl ?: s.backdropUrl,
        facts = facts(s.year, s.rating, info?.genre ?: s.genre, null) +
            listOfNotNull(seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 seizoen" else "$it seizoenen" }),
        plot = info?.plot ?: s.plot,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (next != null) {
                    val resumeAt = positions[next.episodeId]?.let { ResumePolicy.startPosition(it.positionMs, it.durationMs) } ?: 0
                    val label = "S${next.season} A${next.number}"
                    PrimaryButton(
                        if (resumeAt > 0) "Verder kijken $label (${formatTime(resumeAt)})" else "Afspelen $label",
                        { play(next) },
                        Modifier.focusRequester(primary),
                    )
                } else if (loading) {
                    Text("Afleveringen laden…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                } else {
                    Text("Geen afleveringen beschikbaar.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
                SecondaryButton(if (favorite) "♥︎ In favorieten" else "♡︎ Favoriet", { vm.toggleFavorite() })
            }
        },
        below = {
            if (seasons.size > 1) {
                // Coming down from the buttons lands on the season being shown, not the nearest tab.
                val seasonFocus = remember(seasons) { seasons.associateWith { FocusRequester() } }
                val shownFocus = seasonFocus[shownSeason] ?: FocusRequester.Default
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusRestorer(shownFocus)) {
                    items(seasons) { n ->
                        val last = n == seasons.last()
                        // Focusing a season shows it, so the d-pad never needs an extra OK.
                        ListItemSurface(
                            "Seizoen $n",
                            selected = n == shownSeason,
                            onClick = { season = n },
                            modifier = Modifier.width(160.dp).focusRequester(seasonFocus.getValue(n)).lastInRow(last)
                                .onFocusChanged { if (it.isFocused) season = n },
                        )
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.focusRestorer(),
            ) {
                val shown = episodes.filter { it.season == shownSeason }
                items(shown, key = { it.episodeId }) { e ->
                    EpisodeCard(e, positions[e.episodeId], onClick = { play(e) }, isLast = e == shown.last())
                }
            }
        },
    )
}

@Composable
private fun EpisodeCard(e: EpisodeEntity, position: PlaybackPositionEntity?, onClick: () -> Unit, isLast: Boolean) {
    Column(Modifier.width(200.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.width(200.dp).lastInRow(isLast).aspectRatio(16f / 9f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = Focus.SCALE),
            border = CardDefaults.border(focusedBorder = Focus.border),
            colors = CardDefaults.colors(containerColor = SurfaceColor),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${e.number}", style = MaterialTheme.typography.displaySmall, color = TextSecondary)
                e.imageUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (position != null && position.durationMs > 0) {
                    ProgressBar(position.positionMs.toFloat() / position.durationMs, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Text(
            "${e.number}. ${e.title}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
