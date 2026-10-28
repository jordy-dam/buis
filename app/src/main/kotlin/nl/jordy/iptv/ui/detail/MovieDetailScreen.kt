package nl.jordy.iptv.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import nl.jordy.iptv.data.db.MovieVersionRow
import nl.jordy.iptv.data.repo.MovieVersions
import nl.jordy.iptv.ui.components.Chip
import nl.jordy.iptv.ui.theme.TextSecondary
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.MovieInfo
import nl.jordy.iptv.player.ResumePolicy
import nl.jordy.iptv.ui.MovieDetailRoute
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.player.formatTime
import javax.inject.Inject

/** A version the detail screen offers: the stream and its button text. */
data class Version(val streamId: Int, val label: String, val containerExtension: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val library: LibraryRepository,
) : ViewModel() {
    private val opened = savedState.toRoute<MovieDetailRoute>().streamId

    val movie = MutableStateFlow<MovieEntity?>(null)
    val info = MutableStateFlow<MovieInfo?>(null)

    /** All language versions of this film; one entry when there's only one. */
    val versions = MutableStateFlow<List<Version>>(emptyList())
    /** The version Play, the resume position and the favourite heart refer to. */
    val selected = MutableStateFlow(opened)

    val position = selected.flatMapLatest { library.position(ContentType.MOVIE, it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isFavorite = selected.flatMapLatest { library.isFavorite(ContentType.MOVIE, it) }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            val m = db.catalog().movie(opened) ?: return@launch
            movie.value = m
            val rows = db.catalog().movieVersions(m.name, m.year).ifEmpty { listOf(MovieVersionRow(m, null)) }
            versions.value = rows.zip(MovieVersions.labels(rows)) { r, label -> Version(r.movie.streamId, label, r.movie.containerExtension) }
            // Start on the version watched last, else the one that was opened.
            val watched = library.positions(ContentType.MOVIE).first().filter { p -> rows.any { it.movie.streamId == p.itemId } }
                .associate { it.itemId to it.updatedAt }
            selected.value = MovieVersions.pickDefault(rows.map { it.movie.streamId }, watched, opened)
        }
        viewModelScope.launch { info.value = library.movieInfo(opened) }
    }

    fun select(streamId: Int) { selected.value = streamId }

    fun toggleFavorite() = viewModelScope.launch {
        val m = movie.value ?: return@launch
        library.toggleFavorite(ContentType.MOVIE, selected.value, m.name, m.posterUrl)
    }
}

@Composable
fun MovieDetailScreen(onPlay: (PlayerRoute) -> Unit, vm: MovieDetailViewModel = hiltViewModel()) {
    val movie by vm.movie.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val m = movie ?: return
    val primary = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }

    val resumeAt = position?.let { ResumePolicy.startPosition(it.positionMs, it.durationMs) } ?: 0
    val version = versions.firstOrNull { it.streamId == selected }
    val ext = version?.containerExtension ?: info?.containerExtension ?: m.containerExtension
    fun play(fromStart: Boolean) = onPlay(PlayerRoute(PlayerRoute.Kind.MOVIE, selected, m.name, ext, fromStart = fromStart))

    DetailScaffold(
        title = m.name,
        posterUrl = info?.posterUrl ?: m.posterUrl,
        backdropUrl = info?.backdropUrl,
        facts = facts(info?.year ?: m.year, info?.rating ?: m.rating, info?.genre, info?.durationSecs),
        plot = info?.plot,
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (resumeAt > 0) {
                        PrimaryButton("Hervatten vanaf ${formatTime(resumeAt)}", { play(false) }, Modifier.focusRequester(primary))
                        SecondaryButton("Vanaf begin", { play(true) })
                    } else {
                        PrimaryButton("Afspelen", { play(true) }, Modifier.focusRequester(primary))
                    }
                    SecondaryButton(if (favorite) "♥︎ In favorieten" else "♡︎ Favoriet", { vm.toggleFavorite() })
                }
                // Language versions of the same film: picking one changes what Play starts.
                if (versions.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Versie", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(end = 4.dp))
                        versions.forEach { v -> Chip(v.label, v.streamId == selected) { vm.select(v.streamId) } }
                    }
                }
            }
        },
    )
}
