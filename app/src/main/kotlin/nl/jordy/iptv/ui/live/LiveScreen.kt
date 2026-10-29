package nl.jordy.iptv.ui.live

import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.ui.components.CategoryList
import nl.jordy.iptv.ui.components.bestQuality
import nl.jordy.iptv.ui.components.rememberNowAndNext
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
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.filteredBy
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.ListItemSurface
import nl.jordy.iptv.ui.components.filterToggle
import nl.jordy.iptv.data.settings.FilteredCategories
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import nl.jordy.iptv.data.db.ReminderEntity
import nl.jordy.iptv.data.epg.EpgGuide
import kotlinx.coroutines.flow.first
import android.view.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.produceState
import nl.jordy.iptv.ui.home.whenLabel
import nl.jordy.iptv.data.settings.CategoryGroups
import nl.jordy.iptv.data.repo.FollowRepository
import androidx.compose.ui.Alignment

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val epg: EpgRepository,
    private val library: LibraryRepository,
    private val follows: FollowRepository,
    private val guideStore: EpgGuide,
) : ViewModel() {
    private val dao = db.catalog()
    private val showAll = MutableStateFlow(false)

    val filtered = dao.liveCategories().filteredBy(profiles.prefs, showAll)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories = filtered.map { it?.shown }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The category list: pinned ones on their own panel on top (A–Z), the rest in the provider's order. */
    val sections = combine(categories, profiles.pinned) { cats, pins ->
        cats?.let { all ->
            val pinned = all.filter { CategoryGroups.pinKey(it) in pins }
            if (pinned.isEmpty()) listOf<Pair<String?, List<CategoryEntity>>>(null to all)
            else CategoryGroups.group(pinned, pins).map { it.kind.title to it.categories } + listOf(null to (all - pinned.toSet()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Long-press or Menu on a category: pin it to the top for this profile, or unpin it. */
    fun togglePin(id: String) = viewModelScope.launch {
        val cat = categories.value?.firstOrNull { it.id == id } ?: return@launch
        val key = CategoryGroups.pinKey(cat)
        val pinning = key !in profiles.pinned.first()
        profiles.togglePinned(key)
        // A newly pinned category joins the guide (and so reminders) right away, not after tomorrow's refresh.
        if (pinning) guideStore.refresh()
    }

    /** What's on a channel from now on, for the guide panel. */
    suspend fun guide(streamId: Int): List<EpgEntry> = epg.schedule(streamId)

    val followTerms = follows.terms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminderKeys = follows.reminderKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** OK on a coming programme in the guide: a reminder for just that one, or off again. */
    fun toggleReminder(channel: LiveChannelEntity, p: EpgEntry) = viewModelScope.launch {
        follows.toggleReminder(
            ReminderEntity(0, channel.streamId, p.start, p.stop, p.title, channel.name, channel.categoryId, channel.logoUrl, channel.hasArchive),
        )
    }

    /** Long-press in the guide: follow every programme with this title (a [FollowEntity] term), or stop. */
    fun toggleFollow(title: String) = viewModelScope.launch {
        if (followTerms.value.any { it.equals(title, ignoreCase = true) }) follows.remove(followTerms.value.first { it.equals(title, ignoreCase = true) })
        else follows.add(title)
    }

    fun toggleShowAll() { showAll.value = !showAll.value }

    private val explicitCategory = MutableStateFlow<String?>(null)

    /** The category whose channels are shown: the one last focused, else the first. Survives navigating to the player. */
    val selected = combine(explicitCategory, categories) { picked, cats ->
        // A profile switch can hide the picked category; fall back to the first one then.
        picked?.takeIf { id -> cats?.any { it.id == id } == true } ?: cats?.firstOrNull()?.id
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

    suspend fun nowAndNext(streamId: Int) = epg.nowAndNext(streamId)

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
    val sections by vm.sections.collectAsStateWithLifecycle()
    var guideFor by remember { mutableStateOf<LiveChannelEntity?>(null) }
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val categoryFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    var channelsHaveFocus by remember { mutableStateOf(false) }
    var focusedChannel by remember { mutableStateOf<LiveChannelEntity?>(null) }

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
            sections = sections ?: listOf(null to cats),
            selected = selected,
            onFocus = vm::select,
            onClick = { channelFocus.requestFocus() },
            filter = filtered,
            onToggleFilter = vm::toggleShowAll,
            onLongClick = vm::togglePin,
            modifier = Modifier.width(208.dp).fillMaxHeight().focusRequester(categoryFocus),
        )
        Column(Modifier.padding(start = 16.dp).fillMaxSize()) {
        // "Straks" and the time slot of the focused channel, in a fixed strip: rows never grow.
        NowNextStrip(focusedChannel?.takeIf { channelsHaveFocus }, vm::nowAndNext, guideHint = true)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(channelFocus)
                .focusRestorer()
                .onFocusChanged { channelsHaveFocus = it.hasFocus },
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(channels.itemCount, key = channels.itemKey { "${it.categoryId}/${it.streamId}" }) { index ->
                val channel = channels[index] ?: return@items
                val (now, _) = rememberNowAndNext(channel.streamId, vm::nowAndNext)
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
                    number = index + 1,
                    now = now,
                    favorite = channel.streamId in favorites,
                    quality = bestQuality(channel.badges.split(",")),
                    catchup = channel.hasArchive,
                    onClick = {
                        vm.lastChannel = channel.streamId
                        onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, channel.streamId, channel.name, categoryId = channel.categoryId))
                    },
                    onLongClick = { vm.toggleFavorite(channel) },
                    modifier = Modifier.focusRequester(itemFocus).onFocusChanged { if (it.isFocused) focusedChannel = channel }
                        // Right on a channel: its guide, to scroll through what's on later.
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                guideFor = channel
                                true
                            } else false
                        },
                )
            }
        }
        }
    }
    guideFor?.let { ch ->
        val terms by vm.followTerms.collectAsStateWithLifecycle()
        val reminders by vm.reminderKeys.collectAsStateWithLifecycle()
        GuidePanel(
            channel = ch,
            load = vm::guide,
            followed = terms,
            reminders = reminders,
            onWatch = {
                guideFor = null
                vm.lastChannel = ch.streamId
                onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, ch.streamId, ch.name, categoryId = ch.categoryId))
            },
            onToggleReminder = { vm.toggleReminder(ch, it) },
            onToggleFollow = vm::toggleFollow,
            onDismiss = { guideFor = null },
        )
    }
}

/** Two lines above the channel list: the focused channel's programme now, and what's next. */
@Composable
private fun NowNextStrip(channel: LiveChannelEntity?, load: suspend (Int) -> Pair<EpgEntry?, EpgEntry?>, guideHint: Boolean = false) {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
    fun time(sec: Long) = fmt.format(Instant.ofEpochSecond(sec))
    val (now, next) = if (channel != null) rememberNowAndNext(channel.streamId, load) else null to null
    Column(Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp, top = 2.dp)) {
        when {
            channel == null -> Text("Kies een zender", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            now == null && next == null -> Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            else -> {
                Text(
                    now?.let { "${time(it.start)}–${time(it.stop)}  ${it.title}" } ?: channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                next?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Straks ${time(it.start)}  ${it.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (guideHint) {
                            androidx.tv.material3.Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(start = 16.dp).size(18.dp))
                            Text("gids", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One channel's guide on the right, from now on. The focused programme shows its description, so you
 * can see how the guide names things. OK on what's on now tunes in; OK on something later sets a
 * reminder for just that programme (OK again removes it). Long-press follows every programme with
 * that title, like a "Volgen" term. Back or Left closes.
 */
@Composable
private fun GuidePanel(
    channel: LiveChannelEntity,
    load: suspend (Int) -> List<EpgEntry>,
    followed: List<String>,
    reminders: Set<String>,
    onWatch: () -> Unit,
    onToggleReminder: (EpgEntry) -> Unit,
    onToggleFollow: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val programmes by produceState<List<EpgEntry>?>(null, channel.streamId) { value = load(channel.streamId) }
    val first = remember { FocusRequester() }
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.4f)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.width(560.dp).fillMaxHeight().background(SurfaceColor).padding(start = 24.dp, end = 24.dp, top = SafeArea, bottom = 16.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_LEFT) { onDismiss(); true } else false
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(channel.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("OK: kijken, of een herinnering voor dit programma · lang indrukken: alles met deze titel volgen", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                val list = programmes
                when {
                    list == null -> Unit
                    list.isEmpty() -> Text("Geen gids voor deze zender.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(list, key = { _, p -> p.start }) { i, p ->
                            val nowSec = System.currentTimeMillis() / 1000
                            val running = p.start <= nowSec
                            val reminded = "${channel.streamId}:${p.start}" in reminders
                            val follows = followed.any { p.title.contains(it, ignoreCase = true) }
                            var focused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { if (running) onWatch() else onToggleReminder(p) },
                                onLongClick = { onToggleFollow(p.title) },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                                    .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                colors = Focus.bar(container = Color.Transparent),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    val live = Live
                                    Text(
                                        buildAnnotatedString {
                                            if (running) withStyle(SpanStyle(color = live, fontWeight = FontWeight.Medium)) { append("NU  ") }
                                            else append(whenLabel(p.start) + "  ")
                                            append(p.title)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // A bell for a reminder on this programme, an eye when its title is followed.
                                    when {
                                        reminded -> androidx.tv.material3.Icon(Icons.Filled.NotificationsActive, contentDescription = "Herinnering", tint = live, modifier = Modifier.size(18.dp))
                                        follows -> androidx.tv.material3.Icon(Icons.Filled.Visibility, contentDescription = "Je volgt dit", tint = live, modifier = Modifier.size(18.dp))
                                    }
                                    if (focused && !p.description.isNullOrBlank()) {
                                        Text(p.description!!, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (focused) {
                                        Text("${fmt.format(Instant.ofEpochSecond(p.start))}–${fmt.format(Instant.ofEpochSecond(p.stop))}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(programmes) { if (!programmes.isNullOrEmpty()) runCatching { first.requestFocus() } }
}

