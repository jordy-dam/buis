package nl.jordy.iptv.ui.detail

import androidx.compose.foundation.layout.Arrangement
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

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val library: LibraryRepository,
) : ViewModel() {
    val streamId = savedState.toRoute<MovieDetailRoute>().streamId

    val movie = MutableStateFlow<MovieEntity?>(null)
    val info = MutableStateFlow<MovieInfo?>(null)
    val position = library.position(ContentType.MOVIE, streamId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isFavorite = library.isFavorite(ContentType.MOVIE, streamId).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { movie.value = db.catalog().movie(streamId) }
        viewModelScope.launch { info.value = library.movieInfo(streamId) }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val m = movie.value ?: return@launch
        library.toggleFavorite(ContentType.MOVIE, m.streamId, m.name, m.posterUrl)
    }
}

@Composable
fun MovieDetailScreen(onPlay: (PlayerRoute) -> Unit, vm: MovieDetailViewModel = hiltViewModel()) {
    val movie by vm.movie.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val m = movie ?: return
    val primary = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }

    val resumeAt = position?.let { ResumePolicy.startPosition(it.positionMs, it.durationMs) } ?: 0
    val ext = info?.containerExtension ?: m.containerExtension
    fun play(fromStart: Boolean) = onPlay(PlayerRoute(PlayerRoute.Kind.MOVIE, m.streamId, m.name, ext, fromStart = fromStart))

    DetailScaffold(
        title = m.name,
        posterUrl = info?.posterUrl ?: m.posterUrl,
        backdropUrl = info?.backdropUrl,
        facts = facts(info?.year ?: m.year, info?.rating ?: m.rating, info?.genre, info?.durationSecs),
        plot = info?.plot,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (resumeAt > 0) {
                    PrimaryButton("Hervatten vanaf ${formatTime(resumeAt)}", { play(false) }, Modifier.focusRequester(primary))
                    SecondaryButton("Vanaf begin", { play(true) })
                } else {
                    PrimaryButton("Afspelen", { play(true) }, Modifier.focusRequester(primary))
                }
                SecondaryButton(if (favorite) "♥︎ In favorieten" else "♡︎ Favoriet", { vm.toggleFavorite() })
            }
        },
    )
}
