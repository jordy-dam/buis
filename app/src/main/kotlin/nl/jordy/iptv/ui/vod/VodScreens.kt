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
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import nl.jordy.iptv.data.repo.Genres
import nl.jordy.iptv.data.settings.CategoryGroups
import nl.jordy.iptv.data.settings.FilteredCategories
import nl.jordy.iptv.ui.components.GenreChips
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.MovieTile
import nl.jordy.iptv.data.db.SeriesEntity
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.filteredBy
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.components.PosterTile
import javax.inject.Inject

private val pagingConfig = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false)

/** Category list with "Vastgezet", "Diensten", "Genres" and "Overig" headers, after the country filter. */
private fun Flow<FilteredCategories?>.sections(pinned: Flow<Set<String>>) = combine(this, pinned) { f, pins ->
    f?.shown?.let { shown ->
        val groups = CategoryGroups.group(shown, pins)
        // One kind only: no header needed.
        if (groups.size == 1) listOf(null to groups[0].categories) else groups.map { it.kind.title to it.categories }
    }
}

/**
 * The category shown in the grid: the one last focused while it's still listed, else the first
 * in the list as shown (so the first service, not the provider's first category).
 */
private fun selectedOf(picked: Flow<String?>, sections: Flow<List<Pair<String?, List<CategoryEntity>>>?>) =
    combine(picked, sections) { id, s ->
        val shown = s.orEmpty().flatMap { it.second }
        id?.takeIf { p -> shown.any { it.id == p } } ?: shown.firstOrNull()?.id
    }.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(db: AppDatabase, private val profiles: ProfileRepository, private val library: LibraryRepository) : ViewModel() {
    private val dao = db.catalog()
    val focusMemory = FocusMemory()

    private val showAll = MutableStateFlow(false)
    val filtered = dao.movieCategories().filteredBy(profiles.prefs, showAll).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sections = filtered.sections(profiles.pinned).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun toggleShowAll() { showAll.value = !showAll.value }
    /** Long-press or Menu on a category: pin it to the top for this profile, or unpin it. */
    fun togglePin(id: String) = viewModelScope.launch {
        filtered.value?.shown?.firstOrNull { it.id == id }?.let { profiles.togglePinned(CategoryGroups.pinKey(it)) }
    }

    private val picked = MutableStateFlow<String?>(null)
    val selected = selectedOf(picked, sections).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun select(id: String) {
        if (picked.value != id) genre.value = null // a genre belongs to the category it was picked in
        picked.value = id
    }

    /** Film genres come from [nl.jordy.iptv.data.repo.MovieEnricher], so chips appear as details arrive. */
    val genre = MutableStateFlow<String?>(null)
    val genres = selected.filterNotNull().flatMapLatest { id -> dao.movieGenres(id).map { Genres.top(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One tile per film; its language versions are picked on the detail screen. */
    val items: Flow<PagingData<MovieTile>> = combine(selected.filterNotNull(), genre) { id, g -> id to g }.flatMapLatest { (id, g) ->
        Pager(pagingConfig) {
            if (g == null) dao.movieTilesPaged(id) else Genres.patterns(g).let { (a, b) -> dao.movieTilesPagedByGenre(id, a, b) }
        }.flow
    }.cachedIn(viewModelScope)

    val favorites = library.favoriteIds(ContentType.MOVIE).map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    /** streamId → fraction watched, for the progress bar on posters. */
    val progress = library.positions(ContentType.MOVIE)
        .map { list -> list.filter { it.durationMs > 0 }.associate { it.itemId to it.positionMs.toFloat() / it.durationMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleFavorite(m: MovieEntity) = viewModelScope.launch { library.toggleFavorite(ContentType.MOVIE, m.streamId, m.name, m.posterUrl) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(db: AppDatabase, private val profiles: ProfileRepository, private val library: LibraryRepository) : ViewModel() {
    private val dao = db.catalog()
    val focusMemory = FocusMemory()

    private val showAll = MutableStateFlow(false)
    val filtered = dao.seriesCategories().filteredBy(profiles.prefs, showAll).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sections = filtered.sections(profiles.pinned).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun toggleShowAll() { showAll.value = !showAll.value }
    /** Long-press or Menu on a category: pin it to the top for this profile, or unpin it. */
    fun togglePin(id: String) = viewModelScope.launch {
        filtered.value?.shown?.firstOrNull { it.id == id }?.let { profiles.togglePinned(CategoryGroups.pinKey(it)) }
    }

    private val picked = MutableStateFlow<String?>(null)
    val selected = selectedOf(picked, sections).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun select(id: String) {
        if (picked.value != id) genre.value = null // a genre belongs to the category it was picked in
        picked.value = id
    }

    /** Series genres come with the catalog (`get_series`), so a category can be filtered right away. */
    val genre = MutableStateFlow<String?>(null)
    val genres = selected.filterNotNull().mapLatest { Genres.top(dao.seriesGenres(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: Flow<PagingData<SeriesEntity>> = combine(selected.filterNotNull(), genre) { id, g -> id to g }.flatMapLatest { (id, g) ->
        Pager(pagingConfig) {
            if (g == null) dao.seriesPaged(id) else Genres.patterns(g).let { (a, b) -> dao.seriesPagedByGenre(id, a, b) }
        }.flow
    }.cachedIn(viewModelScope)

    val favorites = library.favoriteIds(ContentType.SERIES).map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(s: SeriesEntity) = viewModelScope.launch { library.toggleFavorite(ContentType.SERIES, s.seriesId, s.name, s.coverUrl) }
}

@Composable
fun MoviesScreen(onOpenMovie: (Int) -> Unit, vm: MoviesViewModel = hiltViewModel()) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val genre by vm.genre.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    CategoryBrowser(sections, filtered, vm::toggleShowAll, selected, vm::select, items, { it.movie.streamId }, vm.focusMemory, onPin = vm::togglePin, above = {
        GenreChips(genres, genre, { vm.genre.value = it })
    }, tile = { tile, modifier ->
        val movie = tile.movie
        PosterTile(
            title = movie.name,
            imageUrl = movie.posterUrl,
            width = GridPosterWidth,
            label = if (tile.versions > 1) "${tile.versions} versies" else null,
            favorite = movie.streamId in favorites,
            progress = progress[movie.streamId],
            onClick = {
                vm.focusMemory.opened("$selected/${movie.streamId}")
                onOpenMovie(movie.streamId)
            },
            onLongClick = { vm.toggleFavorite(movie) },
            modifier = modifier,
        )
    })
}

@Composable
fun SeriesScreen(onOpenSeries: (Int) -> Unit, vm: SeriesViewModel = hiltViewModel()) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val genre by vm.genre.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    CategoryBrowser(
        sections, filtered, vm::toggleShowAll, selected, vm::select, items, { it.seriesId }, vm.focusMemory, onPin = vm::togglePin,
        tile = { series, modifier ->
            PosterTile(
                title = series.name,
                imageUrl = series.coverUrl,
                width = GridPosterWidth,
                favorite = series.seriesId in favorites,
                onClick = {
                    vm.focusMemory.opened("$selected/${series.seriesId}")
                    onOpenSeries(series.seriesId)
                },
                onLongClick = { vm.toggleFavorite(series) },
                modifier = modifier,
            )
        },
        above = { GenreChips(genres, genre, { vm.genre.value = it }) },
    )
}
