package nl.jordy.iptv.ui.vod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.SeriesEntity
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.filteredBy
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.components.PosterTile
import javax.inject.Inject

private val pagingConfig = PagingConfig(pageSize = 30, prefetchDistance = 15, enablePlaceholders = false)

@HiltViewModel
class MoviesViewModel @Inject constructor(db: AppDatabase, profiles: ProfileRepository, private val library: LibraryRepository) : ViewModel() {
    private val dao = db.catalog()
    private val rows = mutableMapOf<String, Flow<PagingData<MovieEntity>>>()
    val focusMemory = FocusMemory()

    private val showAll = MutableStateFlow(false)
    val categories = dao.movieCategories().filteredBy(profiles.prefs, showAll).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun toggleShowAll() { showAll.value = !showAll.value }
    val favorites = library.favoriteIds(ContentType.MOVIE).map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** streamId → fraction watched, for the progress bar on posters. */
    val progress = library.positions(ContentType.MOVIE)
        .map { list -> list.filter { it.durationMs > 0 }.associate { it.itemId to it.positionMs.toFloat() / it.durationMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun row(categoryId: String) = rows.getOrPut(categoryId) {
        Pager(pagingConfig) { dao.moviesPaged(categoryId) }.flow.cachedIn(viewModelScope)
    }

    fun toggleFavorite(m: MovieEntity) = viewModelScope.launch { library.toggleFavorite(ContentType.MOVIE, m.streamId, m.name, m.posterUrl) }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(db: AppDatabase, profiles: ProfileRepository, private val library: LibraryRepository) : ViewModel() {
    private val dao = db.catalog()
    private val rows = mutableMapOf<String, Flow<PagingData<SeriesEntity>>>()
    val focusMemory = FocusMemory()

    private val showAll = MutableStateFlow(false)
    val categories = dao.seriesCategories().filteredBy(profiles.prefs, showAll).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun toggleShowAll() { showAll.value = !showAll.value }
    val favorites = library.favoriteIds(ContentType.SERIES).map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun row(categoryId: String) = rows.getOrPut(categoryId) {
        Pager(pagingConfig) { dao.seriesPaged(categoryId) }.flow.cachedIn(viewModelScope)
    }

    fun toggleFavorite(s: SeriesEntity) = viewModelScope.launch { library.toggleFavorite(ContentType.SERIES, s.seriesId, s.name, s.coverUrl) }
}

@Composable
fun MoviesScreen(onOpenMovie: (Int) -> Unit, vm: MoviesViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    CategoryRows("Films", categories, vm::toggleShowAll, vm::row, { it.streamId }, vm.focusMemory) { movie, categoryId, modifier ->
        PosterTile(
            title = movie.name,
            imageUrl = movie.posterUrl,
            favorite = movie.streamId in favorites,
            progress = progress[movie.streamId],
            onClick = {
                vm.focusMemory.opened("$categoryId/${movie.streamId}")
                onOpenMovie(movie.streamId)
            },
            onLongClick = { vm.toggleFavorite(movie) },
            modifier = modifier,
        )
    }
}

@Composable
fun SeriesScreen(onOpenSeries: (Int) -> Unit, vm: SeriesViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    CategoryRows("Series", categories, vm::toggleShowAll, vm::row, { it.seriesId }, vm.focusMemory) { series, categoryId, modifier ->
        PosterTile(
            title = series.name,
            imageUrl = series.coverUrl,
            favorite = series.seriesId in favorites,
            onClick = {
                vm.focusMemory.opened("$categoryId/${series.seriesId}")
                onOpenSeries(series.seriesId)
            },
            onLongClick = { vm.toggleFavorite(series) },
            modifier = modifier,
        )
    }
}
