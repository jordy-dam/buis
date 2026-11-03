package nl.jordy.iptv.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.FavoriteEntity
import nl.jordy.iptv.data.db.GuideChannelEntity
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.Guide
import nl.jordy.iptv.data.repo.GuideRepository
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.ListItemSurface
import nl.jordy.iptv.ui.components.PosterTile
import nl.jordy.iptv.ui.components.rememberNowAndNext
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

/** What the left column of Favorieten can show on the right. */
sealed interface FavoritesEntry {
    data class OfGuide(val id: Long) : FavoritesEntry
    data class OfType(val type: ContentType) : FavoritesEntry
}

data class FavoritesState(val guides: List<Guide>, val movies: List<FavoriteEntity>, val series: List<FavoriteEntity>) {
    val entries: List<FavoritesEntry> = guides.map { FavoritesEntry.OfGuide(it.id) } +
        listOfNotNull(
            FavoritesEntry.OfType(ContentType.MOVIE).takeIf { movies.isNotEmpty() },
            FavoritesEntry.OfType(ContentType.SERIES).takeIf { series.isNotEmpty() },
        )
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val guides: GuideRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    suspend fun nowAndNext(streamId: Int) = epg.nowAndNext(streamId)

    val state = combine(guides.guides, library.favorites()) { g, favs ->
        FavoritesState(g, favs.filter { it.type == ContentType.MOVIE }, favs.filter { it.type == ContentType.SERIES })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val picked = MutableStateFlow<FavoritesEntry?>(null)

    /** The entry shown on the right: the one last focused while it still exists, else the first. */
    val selected = combine(picked, state) { p, s -> p?.takeIf { s?.entries?.contains(it) == true } ?: s?.entries?.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun select(e: FavoritesEntry) { picked.value = e }

    fun create(name: String) = viewModelScope.launch { picked.value = FavoritesEntry.OfGuide(guides.create(name)) }
    fun rename(g: Guide, name: String) = viewModelScope.launch { guides.rename(g.id, name) }
    fun delete(g: Guide) = viewModelScope.launch { guides.delete(g.id) }
    fun move(c: GuideChannelEntity, delta: Int) = viewModelScope.launch { guides.move(c.guideId, c.streamId, delta) }
    fun removeChannel(c: GuideChannelEntity) = viewModelScope.launch { guides.remove(c.guideId, c.streamId) }
    fun remove(f: FavoriteEntity) = viewModelScope.launch { library.toggleFavorite(f.type, f.itemId, f.name, f.imageUrl) }
}

/**
 * The watching profile's guides, then their favourite films and series, on the left; the focused
 * one on the right (the Live TV pattern). OK on a guide opens it as a timeline grid ("Gids"; Right
 * still goes into its channels). Long-press on a guide renames or deletes it, on a
 * channel shows buttons in its row to move it or take it out.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FavoritesScreen(
    onPlay: (PlayerRoute) -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenGuide: (Long) -> Unit,
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val s = loaded ?: return
    val listFocus = remember { FocusRequester() }
    // Entering from the rail lands on the entry shown on the right, as in Live TV.
    val selectedFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Guide?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Guide?>(null) }
    // The channel whose row shows the move buttons, by stream id.
    var editing by remember { mutableStateOf<Int?>(null) }
    val rowFocus = remember { mutableMapOf<Int, FocusRequester>() }
    val channelList = rememberLazyListState()

    BackHandler(enabled = contentHasFocus) { listFocus.requestFocus() }
    // Declared later, so it goes first: Back from the buttons returns to their row, not to the left.
    BackHandler(enabled = editing != null) {
        val id = editing
        editing = null
        runCatching { id?.let { rowFocus[it]?.requestFocus() } }
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(208.dp).fillMaxHeight().focusRequester(listFocus).focusRestorer(selectedFocus).focusGroup(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(s.guides, key = { "guide/${it.id}" }) { g ->
                val entry = FavoritesEntry.OfGuide(g.id)
                ListItemSurface(
                    text = g.name,
                    selected = entry == selected,
                    onClick = { if (g.channels.isNotEmpty()) onOpenGuide(g.id) else runCatching { contentFocus.requestFocus() } },
                    onLongClick = { menuFor = g },
                    modifier = Modifier.selectedFocus(entry == selected, selectedFocus).onFocusChanged { if (it.isFocused) vm.select(entry) },
                )
            }
            listOf(ContentType.MOVIE to "Films", ContentType.SERIES to "Series").forEach { (type, label) ->
                val entry = FavoritesEntry.OfType(type)
                if (entry in s.entries) item(key = "type/$type") {
                    ListItemSurface(
                        text = label,
                        selected = entry == selected,
                        onClick = { runCatching { contentFocus.requestFocus() } },
                        modifier = Modifier.selectedFocus(entry == selected, selectedFocus).onFocusChanged { if (it.isFocused) vm.select(entry) },
                    )
                }
            }
            item(key = "new") { ListItemSurface(text = "+ Nieuwe gids", selected = false, onClick = { creating = true }) }
        }
        Column(Modifier.padding(start = 16.dp).fillMaxSize().onFocusChanged { contentHasFocus = it.hasFocus }) {
            when (val e = selected) {
                null -> Hint("Nog geen favorieten. Houd OK ingedrukt op een zender in Live TV om hem in Mijn gids te zetten, of op een film of serie.")
                is FavoritesEntry.OfGuide -> {
                    val guide = s.guides.firstOrNull { it.id == e.id } ?: return@Column
                    if (guide.channels.isEmpty()) {
                        Hint("${guide.name} is nog leeg. Houd OK ingedrukt op een zender in Live TV om hem toe te voegen.")
                    }
                    // The row being moved stays in view as it goes past the top or bottom edge.
                    val at = guide.channels.indexOfFirst { it.streamId == editing }
                    LaunchedEffect(at) {
                        val visible = channelList.layoutInfo.visibleItemsInfo
                        if (at < 0 || visible.isEmpty()) return@LaunchedEffect
                        val first = visible.first().index
                        val last = visible.last().index
                        if (at <= first) channelList.animateScrollToItem(at) else if (at >= last) channelList.animateScrollToItem(at - (last - first) + 1)
                    }
                    LazyColumn(
                        state = channelList,
                        modifier = Modifier.fillMaxSize().focusRequester(contentFocus).focusRestorer(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(guide.channels, key = { _, c -> "${c.guideId}/${c.streamId}" }) { i, c ->
                            val tile = rowFocus.getOrPut(c.streamId) { FocusRequester() }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChannelTile(
                                    // Channels that came from favourites before 2a hold raw provider names.
                                    name = NameParser.parse(c.name).title,
                                    logoUrl = c.logoUrl,
                                    number = i + 1,
                                    now = rememberNowAndNext(c.streamId, vm::nowAndNext).first,
                                    onClick = { onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, c.streamId, c.name, categoryId = PlayerRoute.guideCategory(guide.id))) },
                                    onLongClick = { editing = c.streamId },
                                    modifier = Modifier.weight(1f).focusRequester(tile),
                                )
                                if (editing == c.streamId) {
                                    GuideChannelEdit(
                                        onMove = { vm.move(c, it) },
                                        onRemove = {
                                            // Focus first goes to the row that takes its place, so it isn't lost with it.
                                            val next = guide.channels.getOrNull(i + 1) ?: guide.channels.getOrNull(i - 1)
                                            runCatching { next?.let { rowFocus[it.streamId]?.requestFocus() } ?: listFocus.requestFocus() }
                                            editing = null
                                            vm.removeChannel(c)
                                        },
                                        onDone = { editing = null; runCatching { tile.requestFocus() } },
                                        onLeave = { if (editing == c.streamId) editing = null },
                                    )
                                }
                            }
                        }
                    }
                }
                is FavoritesEntry.OfType -> {
                    val items = if (e.type == ContentType.MOVIE) s.movies else s.series
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize().focusRequester(contentFocus).focusRestorer(),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items, key = { "${e.type}/${it.itemId}" }) { f ->
                            PosterTile(
                                title = NameParser.parse(f.name).title,
                                imageUrl = f.imageUrl,
                                favorite = true,
                                onClick = { if (e.type == ContentType.MOVIE) onOpenMovie(f.itemId) else onOpenSeries(f.itemId) },
                                onLongClick = { vm.remove(f) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        GuideNameDialog("Nieuwe gids", "", onSave = { vm.create(it); creating = false }, onDismiss = { creating = false })
    }
    naming?.let { g ->
        GuideNameDialog("Naam wijzigen", g.name, onSave = { vm.rename(g, it); naming = null }, onDismiss = { naming = null })
    }
    menuFor?.let { g ->
        GuideMenuDialog(
            g,
            onRename = { menuFor = null; naming = g },
            onDelete = { vm.delete(g); menuFor = null; listFocus.requestFocus() },
            onDismiss = { menuFor = null },
        )
    }
}

private fun Modifier.selectedFocus(selected: Boolean, requester: FocusRequester) = if (selected) focusRequester(requester) else this

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = TextSecondary,
    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
)
