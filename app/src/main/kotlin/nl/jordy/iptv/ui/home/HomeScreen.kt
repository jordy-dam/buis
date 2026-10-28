package nl.jordy.iptv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
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
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.PosterTile
import nl.jordy.iptv.ui.components.lastInRow
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

/** A "Verder kijken" tile: a film or an episode, ready to resume. */
data class ContinueItem(val key: String, val title: String, val imageUrl: String?, val progress: Float, val play: PlayerRoute)

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
    library: LibraryRepository,
    profiles: ProfileRepository,
) : ViewModel() {

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
        return when (p.type) {
            ContentType.MOVIE -> db.catalog().movie(p.itemId)?.let { m ->
                ContinueItem("m${m.streamId}", m.name, m.posterUrl, progress, PlayerRoute(PlayerRoute.Kind.MOVIE, m.streamId, m.name, m.containerExtension))
            }
            ContentType.SERIES -> {
                val e = db.episodes().episode(p.itemId) ?: return null
                val s = db.catalog().series(e.seriesId)
                val title = "${s?.name ?: e.title} · S${e.season} A${e.number}"
                ContinueItem("e${e.episodeId}", title, s?.coverUrl ?: e.imageUrl, progress, PlayerRoute(PlayerRoute.Kind.EPISODE, e.episodeId, title, e.containerExtension))
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
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(s.continueWatching, key = { it.key }) { c ->
                        PosterTile(
                            title = c.title,
                            imageUrl = c.imageUrl,
                            progress = c.progress,
                            onClick = { onPlay(c.play) },
                            onLongClick = {},
                            modifier = Modifier.lastInRow(c == s.continueWatching.last()),
                        )
                    }
                }
            }
        }
        if (s.channels.isNotEmpty()) {
            item(key = "channels") {
                Text("Favoriete zenders", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(s.channels, key = { it.itemId }) { f ->
                        ChannelTile(
                            name = NameParser.parse(f.name).title,
                            logoUrl = f.imageUrl,
                            nowPlaying = null,
                            // Zapping from here goes through the favourite channels, as on Favorieten.
                            onClick = { onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, f.itemId, f.name, categoryId = PlayerRoute.FAVORITES_CATEGORY)) },
                            onLongClick = {},
                            modifier = Modifier.width(320.dp).lastInRow(f == s.channels.last()),
                        )
                    }
                }
            }
        }
        if (s.recent.isNotEmpty()) {
            item(key = "recent") {
                Text("Recent toegevoegd", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
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
