package nl.jordy.iptv.ui.guide

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.db.ReminderEntity
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.FollowRepository
import nl.jordy.iptv.data.repo.GuideRepository
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.player.Catchup
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.TvGuideRoute
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.guide.GuideGrid.Block
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.FocusFill
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.RowSurface
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** A channel of the guide, with its catalog row (archive, category) when the catalog still has it. */
data class GridChannel(val streamId: Int, val name: String, val logoUrl: String?, val live: LiveChannelEntity?)

data class TvGuideState(val name: String, val channels: List<GridChannel>, val rows: List<List<Block>>, val from: Long, val to: Long)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val guides: GuideRepository,
    private val epg: EpgRepository,
    private val db: AppDatabase,
    private val follows: FollowRepository,
) : ViewModel() {
    val guideId = savedState.toRoute<TvGuideRoute>().guideId

    /** An hour back (the stored guide keeps two), on the half hour. */
    private val from = GuideGrid.floorHalfHour(System.currentTimeMillis() / 1000 - 3600)
    private var loading = false

    val state = MutableStateFlow<TvGuideState?>(null)
    val reminderKeys = follows.reminderKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val followTerms = follows.terms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { load(from + STEP) }
    }

    /** Six more hours, as the focus nears the end; the stored guide runs about a day and a half ahead. */
    fun extend() {
        val s = state.value ?: return
        if (loading || s.to >= from + MAX) return
        viewModelScope.launch { load(minOf(s.to + STEP, from + MAX)) }
    }

    private suspend fun load(to: Long) {
        loading = true
        val guide = guides.guides.first().firstOrNull { it.id == guideId }
        val ids = guide?.channels?.map { it.streamId }.orEmpty()
        val live = db.catalog().liveByIds(ids).associateBy { it.streamId }
        val channels = guide?.channels.orEmpty().map {
            // Channels that came from favourites before 2a hold raw provider names.
            GridChannel(it.streamId, NameParser.parse(it.name).title, it.logoUrl, live[it.streamId])
        }
        val programmes = epg.window(ids, from, to)
        val rows = withContext(Dispatchers.Default) { channels.map { GuideGrid.row(programmes[it.streamId].orEmpty(), from, to) } }
        state.value = TvGuideState(guide?.name.orEmpty(), channels, rows, from, to)
        loading = false
    }

    fun toggleReminder(c: GridChannel, p: EpgEntry) = viewModelScope.launch {
        follows.toggleReminder(
            ReminderEntity(0, c.streamId, p.start, p.stop, p.title, c.name, c.live?.categoryId.orEmpty(), c.logoUrl, c.live?.hasArchive == true),
        )
    }

    /** Follow every programme with this title (a Volgen term), or stop. */
    fun toggleFollow(title: String) = viewModelScope.launch {
        val term = followTerms.value.firstOrNull { it.equals(title, ignoreCase = true) }
        if (term != null) follows.remove(term) else follows.add(title)
    }

    private companion object {
        const val STEP = 7 * 3600L
        const val MAX = 36 * 3600L
    }
}

private val ChannelWidth = 220.dp
private val RowHeight = 48.dp
private val RulerHeight = 28.dp
private val HeaderHeight = 116.dp
/** 5 dp a minute: half an hour is 150 dp, a bit over two hours fit across. */
private const val DP_PER_MINUTE = 5f
/** Blocks this far outside the visible stretch stay composed, so a scroll animation never shows holes. */
private const val MARGIN = 2 * 3600L

/** What OK on a programme that isn't on now opens. */
private sealed interface Ask {
    val channel: GridChannel
    val programme: EpgEntry
    data class Past(override val channel: GridChannel, override val programme: EpgEntry) : Ask
    data class Future(override val channel: GridChannel, override val programme: EpgEntry) : Ask
}

/**
 * "Gids": one of the profile's own guides as a timeline grid. Channels down the left, half hours
 * across, a line at now and the focused programme in a header on top. The grid is one focus target
 * that moves its own focus: Up/Down changes channel at the same time, Left/Right goes programme by
 * programme, so focus can't wander off into the channel column or the header. OK on what's on now
 * plays the channel (zapping through this guide); on something past offers "Vanaf begin" where the
 * channel keeps an archive, on something later a reminder. Back leaves.
 */
@Composable
fun TvGuideScreen(onPlay: (PlayerRoute) -> Unit, vm: TvGuideViewModel = hiltViewModel()) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    val reminders by vm.reminderKeys.collectAsStateWithLifecycle()
    val terms by vm.followTerms.collectAsStateWithLifecycle()
    val s = loaded
    Box(Modifier.fillMaxSize().padding(start = SafeArea, end = SafeArea, top = 32.dp)) {
        when {
            s == null -> Text("Gids laden…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            s.channels.isEmpty() -> Text("Deze gids heeft nog geen zenders.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            else -> Grid(s, reminders, terms, onPlay, vm)
        }
    }
}

@Composable
private fun Grid(s: TvGuideState, reminders: Set<String>, terms: List<String>, onPlay: (PlayerRoute) -> Unit, vm: TvGuideViewModel) {
    val nowSec by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    val startNow = remember { System.currentTimeMillis() / 1000 }
    var row by rememberSaveable { mutableIntStateOf(0) }
    // A time inside the focused block (its start), and the time Up/Down tries to keep.
    var focusAt by rememberSaveable { mutableLongStateOf(startNow) }
    var anchor by rememberSaveable { mutableLongStateOf(startNow) }
    var viewStart by rememberSaveable { mutableLongStateOf(maxOf(s.from, GuideGrid.floorHalfHour(startNow) - GuideGrid.HALF_HOUR)) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    val gridFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val pxPerMinute = with(density) { DP_PER_MINUTE.dp.toPx() }
    val scroll = remember { Animatable(GuideGrid.x(viewStart, s.from, pxPerMinute)) }
    LaunchedEffect(viewStart) { scroll.animateTo(GuideGrid.x(viewStart, s.from, pxPerMinute), tween(160)) }

    row = row.coerceIn(0, s.channels.lastIndex)
    val focused = GuideGrid.target(s.rows[row], focusAt)
    val block = s.rows[row].getOrNull(focused)
    val channel = s.channels[row]

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val span = ((maxWidth - ChannelWidth).value / DP_PER_MINUTE * 60).toLong()

        // The focus nears the end of what's loaded: load the next hours before it gets there.
        LaunchedEffect(viewStart, s.to) { if (viewStart + span + 3600 > s.to) vm.extend() }

        // The focused channel stays in view; the list itself never takes focus.
        LaunchedEffect(row) {
            val info = list.layoutInfo
            val whole = info.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset }
            val first = whole.firstOrNull()?.index ?: return@LaunchedEffect
            val last = whole.last().index
            if (row < first) list.animateScrollToItem(row) else if (row > last) list.animateScrollToItem(row - (last - first))
        }

        fun focus(newRow: Int, b: Block, moveAnchor: Boolean) {
            row = newRow
            focusAt = b.start
            viewStart = GuideGrid.scrollFor(b, viewStart, span, s.from, s.to)
            if (moveAnchor) anchor = maxOf(b.start, viewStart)
        }

        fun open(c: GridChannel, b: Block) {
            val p = b.programme
            val live = PlayerRoute(PlayerRoute.Kind.LIVE, c.streamId, c.name, categoryId = PlayerRoute.guideCategory(vm.guideId))
            val now = System.currentTimeMillis() / 1000
            when {
                p == null || now in p.start until p.stop -> onPlay(live)
                p.stop <= now -> ask = Ask.Past(c, p)
                else -> ask = Ask.Future(c, p)
            }
        }

        Column(
            Modifier.fillMaxSize().focusRequester(gridFocus).focusable().onKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                // From state, not from the last composition: keys (and a held key) can come faster than frames.
                val at = GuideGrid.target(s.rows[row], focusAt)
                when (e.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val to = (row + if (e.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1).coerceIn(0, s.channels.lastIndex)
                        if (down && to != row) s.rows[to].getOrNull(GuideGrid.target(s.rows[to], anchor))?.let { focus(to, it, moveAnchor = false) }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val next = at + if (e.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        if (down) s.rows[row].getOrNull(next)?.let { focus(row, it, moveAnchor = true) }
                        true
                    }
                    // On release, so the dialog it may open doesn't get the key-up as a click.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (!down) s.rows[row].getOrNull(at)?.let { open(s.channels[row], it) }
                        true
                    }
                    else -> false
                }
            },
        ) {
            Header(channel, block, s.name, nowSec)
            Ruler(s.from, viewStart, span, pxPerMinute) { scroll.value }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                    items(s.channels.size, key = { s.channels[it].streamId }) { i ->
                        val c = s.channels[i]
                        Row(Modifier.fillMaxWidth().height(RowHeight)) {
                            ChannelCell(i + 1, c, i == row)
                            Timeline(
                                blocks = s.rows[i],
                                from = s.from,
                                shownFrom = viewStart - MARGIN,
                                shownTo = viewStart + span + MARGIN,
                                focused = if (i == row) focused else -1,
                                nowSec = nowSec,
                                archive = c.live?.hasArchive == true,
                                reminded = { p -> "${c.streamId}:${p.start}" in reminders },
                                pxPerMinute = pxPerMinute,
                                scroll = { scroll.value },
                            )
                        }
                    }
                }
                // The line at now, over the rows, cut off where the channel column starts.
                Box(Modifier.padding(start = ChannelWidth).fillMaxSize().clipToBounds()) {
                    val x = GuideGrid.x(nowSec, s.from, pxPerMinute)
                    Box(Modifier.offset { IntOffset((x - scroll.value).roundToInt(), 0) }.width(2.dp).fillMaxHeight().background(Live))
                }
            }
        }
    }

    LaunchedEffect(ask) { if (ask == null) runCatching { gridFocus.requestFocus() } }

    ask?.let { a ->
        val p = a.programme
        when (a) {
            is Ask.Past -> PastDialog(
                a.channel, p,
                canRestart = Catchup.canRestart(a.channel.live, p, System.currentTimeMillis() / 1000),
                onFromStart = {
                    ask = null
                    onPlay(
                        PlayerRoute(
                            PlayerRoute.Kind.LIVE, a.channel.streamId, a.channel.name, categoryId = PlayerRoute.guideCategory(vm.guideId),
                            fromStart = true, catchupStart = p.start, catchupStop = p.stop, catchupTitle = p.title,
                        ),
                    )
                },
                onLive = {
                    ask = null
                    onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, a.channel.streamId, a.channel.name, categoryId = PlayerRoute.guideCategory(vm.guideId)))
                },
                onDismiss = { ask = null },
            )
            is Ask.Future -> FutureDialog(
                a.channel, p,
                reminded = "${a.channel.streamId}:${p.start}" in reminders,
                followed = terms.any { it.equals(p.title, ignoreCase = true) },
                onReminder = { vm.toggleReminder(a.channel, p); ask = null },
                onFollow = { vm.toggleFollow(p.title); ask = null },
                onDismiss = { ask = null },
            )
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dayName = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl"))

private fun time(sec: Long) = clock.format(Instant.ofEpochSecond(sec))

/** "Vandaag", "Morgen" or "Gisteren", else the weekday and date. */
private fun dayLabel(sec: Long): String {
    val day = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (day) {
        LocalDate.now() -> "Vandaag"
        LocalDate.now().plusDays(1) -> "Morgen"
        LocalDate.now().minusDays(1) -> "Gisteren"
        else -> dayName.format(day).replaceFirstChar(Char::uppercase)
    }
}

private fun EpgEntry.slot() = "${time(start)}–${time(stop)} · ${(stop - start) / 60} min"

/** The focused programme: channel and time in amber, the title, then as much description as fits. */
@Composable
private fun Header(channel: GridChannel, block: Block?, guideName: String, nowSec: Long) {
    val p = block?.programme
    Column(Modifier.fillMaxWidth().height(HeaderHeight).padding(start = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(channel.name, p?.let { (if (it.start > nowSec + 12 * 3600 || it.stop < nowSec - 12 * 3600) "${dayLabel(it.start)} " else "") + it.slot() })
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Live,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text("$guideName · OK: kijken, terugkijken of herinnering", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
        }
        Text(
            p?.title ?: "Geen gids",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            p?.description?.takeIf { it.isNotBlank() } ?: if (p == null) "Voor deze zender is er op dit moment geen gids. OK kijkt live." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The day on the left, half hours across, moving with the rows. */
@Composable
private fun Ruler(from: Long, viewStart: Long, span: Long, pxPerMinute: Float, scroll: () -> Float) {
    Row(Modifier.fillMaxWidth().height(RulerHeight)) {
        Text(dayLabel(viewStart), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.width(ChannelWidth).padding(start = 8.dp))
        val ticks = GuideGrid.ticks(viewStart - MARGIN, viewStart + span + MARGIN)
        Layout(
            content = { ticks.forEach { t -> key(t) { Text(time(t), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(start = 6.dp)) } } },
            modifier = Modifier.fillMaxSize().clipToBounds(),
        ) { measurables, c ->
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(c.maxWidth, c.maxHeight) {
                val s = scroll()
                placeables.forEachIndexed { i, p -> p.place((GuideGrid.x(ticks[i], from, pxPerMinute) - s).roundToInt(), 0) }
            }
        }
    }
}

@Composable
private fun ChannelCell(number: Int, c: GridChannel, current: Boolean) {
    Row(
        Modifier.width(ChannelWidth).fillMaxHeight().padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
            .background(if (current) SurfaceRaised else RowSurface, RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(34.dp).padding(start = 8.dp))
        Box(Modifier.height(26.dp).aspectRatio(16f / 9f).background(SurfaceRaised, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text(c.name.take(2).uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            if (c.logoUrl != null) AsyncImage(model = c.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/**
 * One channel's programmes, laid out by time. Only blocks near the visible stretch are composed;
 * [scroll] is read while placing, so scrolling moves blocks without recomposing them.
 */
@Composable
private fun Timeline(
    blocks: List<Block>,
    from: Long,
    shownFrom: Long,
    shownTo: Long,
    focused: Int,
    nowSec: Long,
    archive: Boolean,
    reminded: (EpgEntry) -> Boolean,
    pxPerMinute: Float,
    scroll: () -> Float,
) {
    val shown = blocks.withIndex().filter { it.value.stop > shownFrom && it.value.start < shownTo }
    val gap = with(LocalDensity.current) { 3.dp.roundToPx() }
    Layout(
        content = {
            shown.forEach { (i, b) ->
                key(b.start) {
                    BlockCell(b, i == focused, nowSec, archive, b.programme?.let(reminded) == true) {
                        maxOf(0f, scroll() - GuideGrid.x(b.start, from, pxPerMinute))
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) { measurables, c ->
        val placeables = measurables.mapIndexed { k, m ->
            val b = shown[k].value
            val w = (GuideGrid.x(b.stop, b.start, pxPerMinute).roundToInt() - gap).coerceAtLeast(1)
            m.measure(Constraints.fixed(w, c.maxHeight))
        }
        layout(c.maxWidth, c.maxHeight) {
            val s = scroll()
            placeables.forEachIndexed { k, p -> p.place((GuideGrid.x(shown[k].value.start, from, pxPerMinute) - s).roundToInt(), 0) }
        }
    }
}

/**
 * A programme: title and start time, dimmed once it's over. Its text slides along when the block
 * starts left of the visible stretch ([titleShift]), so a running film still shows its name.
 */
@Composable
private fun BlockCell(b: Block, focused: Boolean, nowSec: Long, archive: Boolean, reminded: Boolean, titleShift: () -> Float) {
    val p = b.programme
    val running = p != null && nowSec in p.start until p.stop
    val past = p != null && p.stop <= nowSec
    val shape = RoundedCornerShape(6.dp)
    val fill = when {
        focused -> FocusFill
        running -> SurfaceRaised
        p == null -> Color.Transparent
        else -> RowSurface
    }
    Box(
        Modifier.fillMaxSize().padding(vertical = 2.dp).clip(shape).background(fill)
            .then(if (focused) Modifier.border(BorderStroke(2.dp, FocusBar), shape) else if (p == null) Modifier.border(BorderStroke(1.dp, RowSurface), shape) else Modifier),
    ) {
        Row(
            Modifier.fillMaxHeight().offset { IntOffset(titleShift().roundToInt(), 0) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    p?.title ?: "Geen gids",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (past || p == null) TextSecondary else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p != null) Text(time(p.start), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
            }
            when {
                reminded -> Icon(Icons.Filled.NotificationsActive, contentDescription = "Herinnering", tint = Live, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                past && archive -> Icon(Icons.Filled.History, contentDescription = "Terugkijken", tint = TextSecondary, modifier = Modifier.padding(start = 6.dp).size(14.dp))
            }
        }
    }
}

@Composable
private fun ProgrammeDialog(c: GridChannel, p: EpgEntry, onDismiss: () -> Unit, note: String?, buttons: @Composable (FocusRequester) -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${c.name}  ·  ${dayLabel(p.start)} ${p.slot()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Live)
            Text(p.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { buttons(first) }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** A programme that's over: from its start where the channel keeps an archive, else only live. */
@Composable
private fun PastDialog(c: GridChannel, p: EpgEntry, canRestart: Boolean, onFromStart: () -> Unit, onLive: () -> Unit, onDismiss: () -> Unit) =
    ProgrammeDialog(c, p, onDismiss, note = if (canRestart) null else "Dit is al geweest en ${c.name} bewaart het niet om terug te kijken.") { first ->
        if (canRestart) {
            PrimaryButton("Vanaf begin", onFromStart, Modifier.focusRequester(first))
            SecondaryButton("Live kijken", onLive)
        } else {
            PrimaryButton("Live kijken", onLive, Modifier.focusRequester(first))
            SecondaryButton("Terug", onDismiss)
        }
    }

/** A programme still to come: a reminder for just this one, or follow everything with its title. */
@Composable
private fun FutureDialog(c: GridChannel, p: EpgEntry, reminded: Boolean, followed: Boolean, onReminder: () -> Unit, onFollow: () -> Unit, onDismiss: () -> Unit) =
    ProgrammeDialog(c, p, onDismiss, note = null) { first ->
        PrimaryButton(if (reminded) "Herinnering weg" else "Herinner me", onReminder, Modifier.focusRequester(first))
        SecondaryButton(if (followed) "Niet meer volgen" else "Alles met deze titel volgen", onFollow)
    }
