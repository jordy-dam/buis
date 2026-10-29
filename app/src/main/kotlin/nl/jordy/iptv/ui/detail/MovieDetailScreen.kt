package nl.jordy.iptv.ui.detail

import nl.jordy.iptv.ui.components.IconButton
import nl.jordy.iptv.ui.components.FavoriteButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.SurfaceColor
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

/** A version the detail screen offers: the stream and how it reads in the version list. */
data class Version(val streamId: Int, val label: MovieVersions.Label, val containerExtension: String)

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
            val tmdb = m.tmdbId ?: db.movieInfo().get(opened)?.tmdbId ?: library.movieInfo(opened)?.tmdbId
            val rows = db.catalog().movieVersions(m.name, m.year, tmdb).ifEmpty { listOf(MovieVersionRow(m, null)) }
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
    var pickVersion by remember { mutableStateOf(false) }

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
                        IconButton(Icons.Filled.RestartAlt, "Vanaf begin", { play(true) })
                    } else {
                        PrimaryButton("Afspelen", { play(true) }, Modifier.focusRequester(primary))
                    }
                    // Language versions of the same film: one button next to Play, the choices in a list.
                    if (versions.size > 1 && version != null) {
                        SecondaryButton("${version.label.language} · ${versions.size} versies", { pickVersion = true })
                    }
                    FavoriteButton(favorite, vm::toggleFavorite)
                }
            }
        },
    )
    if (pickVersion) {
        VersionDialog(versions, selected, onPick = { vm.select(it); pickVersion = false }, onDismiss = { pickVersion = false })
    }
}

/** The versions of a film, two lines each: the language, then title and quality. Focus starts on the current one. */
@Composable
private fun VersionDialog(versions: List<Version>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val current = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(24.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Versie kiezen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
            versions.forEach { v ->
                val isCurrent = v.streamId == selected
                Surface(
                    onClick = { onPick(v.streamId) },
                    modifier = Modifier.fillMaxWidth().then(if (isCurrent) Modifier.focusRequester(current) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = Focus.bar(container = if (isCurrent) Brand.copy(alpha = 0.35f) else Color.Transparent),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(v.label.language, style = MaterialTheme.typography.titleMedium)
                        if (v.label.detail.isNotEmpty()) {
                            Text(v.label.detail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { current.requestFocus() } }
}
