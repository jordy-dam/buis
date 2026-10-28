package nl.jordy.iptv.ui.live

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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.ListItemSurface
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    db: AppDatabase,
    private val epg: EpgRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val dao = db.catalog()

    val categories = dao.liveCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val explicitCategory = MutableStateFlow<String?>(null)

    /** The category whose channels are shown: the one last focused, else the first. Survives navigating to the player. */
    val selected = combine(explicitCategory, categories) { picked, cats ->
        picked ?: cats?.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<LiveChannelEntity>> = selected.filterNotNull().flatMapLatest { id ->
        Pager(PagingConfig(pageSize = 40, prefetchDistance = 20)) { dao.livePaged(id) }.flow
    }.cachedIn(viewModelScope)

    val favorites = library.favoriteIds(ContentType.LIVE).map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Last focused channel, so coming back from the player lands on it. */
    var lastChannel: Int? = null

    fun select(categoryId: String) { explicitCategory.value = categoryId }

    suspend fun nowPlaying(streamId: Int): String? {
        val (now, next) = epg.nowAndNext(streamId)
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        return when {
            now != null -> now.title
            next != null -> "${fmt.format(Instant.ofEpochSecond(next.start))} ${next.title}"
            else -> null
        }
    }

    fun toggleFavorite(c: LiveChannelEntity) = viewModelScope.launch {
        library.toggleFavorite(ContentType.LIVE, c.streamId, c.name, c.logoUrl)
    }
}

/**
 * Categories on the left, channels on the right (TiviMate pattern). Focusing a category shows its
 * channels; right moves into them. From launch: right, OK = a channel playing in two clicks.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(onPlay: (PlayerRoute) -> Unit, vm: LiveViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val categoryFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    var channelsHaveFocus by remember { mutableStateOf(false) }

    // Inside the channel list, Back first returns to the categories; the shell handles the next Back.
    BackHandler(enabled = channelsHaveFocus) { categoryFocus.requestFocus() }

    val cats = categories
    if (cats == null) return
    if (cats.isEmpty()) {
        Text("Geen zenders gevonden bij deze provider.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        return
    }

    Row(Modifier.fillMaxSize()) {
        CategoryList(
            categories = cats,
            selected = selected,
            onFocus = vm::select,
            onClick = { channelFocus.requestFocus() },
            modifier = Modifier.width(320.dp).fillMaxHeight().focusRequester(categoryFocus),
        )
        LazyColumn(
            modifier = Modifier
                .padding(start = 24.dp)
                .fillMaxSize()
                .focusRequester(channelFocus)
                .focusRestorer()
                .onFocusChanged { channelsHaveFocus = it.hasFocus },
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(channels.itemCount, key = channels.itemKey { "${it.categoryId}/${it.streamId}" }) { index ->
                val channel = channels[index] ?: return@items
                val nowPlaying by produceState<String?>(null, channel.streamId) {
                    delay(150) // only for rows that stay on screen while scrolling
                    value = vm.nowPlaying(channel.streamId)
                }
                val itemFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    // One-shot: only right after returning from the player, never when switching tabs.
                    if (vm.lastChannel == channel.streamId) {
                        vm.lastChannel = null
                        runCatching { itemFocus.requestFocus() }
                    }
                }
                ChannelTile(
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    nowPlaying = nowPlaying,
                    favorite = channel.streamId in favorites,
                    onClick = {
                        vm.lastChannel = channel.streamId
                        onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, channel.streamId, channel.name, categoryId = channel.categoryId))
                    },
                    onLongClick = { vm.toggleFavorite(channel) },
                    modifier = Modifier.focusRequester(itemFocus),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoryList(
    categories: List<CategoryEntity>,
    selected: String?,
    onFocus: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Entering from the rail lands on the active category, not on whichever row happens to be nearest.
    val selectedFocus = remember { FocusRequester() }
    LazyColumn(
        modifier = modifier.focusRestorer(selectedFocus).focusGroup(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            ListItemSurface(
                text = cat.name,
                selected = cat.id == selected,
                onClick = onClick,
                modifier = Modifier
                    .then(if (cat.id == selected) Modifier.focusRequester(selectedFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus(cat.id) },
            )
        }
    }
}
