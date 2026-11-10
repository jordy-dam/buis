package nl.jordy.iptv.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.WatchLogEntity
import nl.jordy.iptv.data.repo.WatchKind
import nl.jordy.iptv.data.repo.WatchLog
import nl.jordy.iptv.data.repo.WatchStats
import nl.jordy.iptv.data.repo.WeekSummary
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.settings.SettingRow
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextOnLight
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.sqrt

/** The rows behind the screen, and the live category names that tell sport from news. */
data class HistoryData(val rows: List<WatchLogEntity>, val categories: Map<String, String>)

@HiltViewModel
class HistoryViewModel @Inject constructor(watchLog: WatchLog, db: AppDatabase) : ViewModel() {
    /** Five calendar weeks back, so the grid is full; this week and the one before give the comparison. */
    val data = combine(watchLog.recent(days = CALENDAR_WEEKS * 7 + 1), db.catalog().liveCategories()) { rows, cats ->
        HistoryData(rows, cats.associate { it.id to it.name })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private const val CALENDAR_WEEKS = 5

private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val date = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl"))

/**
 * The bar's shades: the profile's accent (the same [Live] as the label and the calendar dots), a
 * step lighter per kind, biggest first. One colour family, so it follows the look (Standaard, Garage).
 */
private val KindShades = listOf(1f, 0.72f, 0.5f, 0.34f, 0.22f, 0.14f)

/**
 * Kijkgeschiedenis: the week in numbers on the left, a calendar of five weeks on the right with a
 * dot per day that grows with the time watched. Focus a day to see what was on; OK on a day goes to
 * its list, OK on a row plays it again. Back goes back to Instellingen.
 */
@Composable
fun HistoryScreen(onPlay: (PlayerRoute) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val (rows, categories) = data ?: return
    val today = remember { LocalDate.now() }
    val stats = remember(data) { WatchStats.of(rows, categories, today) }
    var selected by remember { mutableStateOf(today) }
    val todayFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val dayRows = remember(rows, selected) {
        rows.filter { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() == selected }
    }

    Row(Modifier.fillMaxSize().background(Background).padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Kijkgeschiedenis", style = MaterialTheme.typography.headlineMedium)
            if (rows.isEmpty()) {
                Text("Nog niets gekeken. Alles wat je langer dan een minuut kijkt, komt hier te staan.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            } else {
                Hero(stats)
                KindBar(stats.byKind)
                Facts(stats)
            }
        }
        Column(Modifier.width(420.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Calendar(
                today = today,
                perDay = stats.perDay,
                todayFocus = todayFocus,
                onFocusDay = { selected = it },
                onOpenDay = { if (dayRows.isNotEmpty()) runCatching { listFocus.requestFocus() } },
            )
            Text(
                dayLabel(selected, today) + (stats.perDay[selected]?.let { " · ${WeekSummary.duration(it)}" } ?: ""),
                style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = 6.dp),
            )
            if (dayRows.isEmpty()) {
                Text("Niets gekeken.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(dayRows, key = { it.id }) { r ->
                        SettingRow(
                            r.title,
                            listOfNotNull(time.format(Instant.ofEpochMilli(r.startedAt)), r.detail ?: kind(r)).joinToString(" · "),
                            value = WeekSummary.duration(r.seconds),
                            onClick = { onPlay(route(r)) },
                            modifier = if (r == dayRows.first()) Modifier.focusRequester(listFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { todayFocus.requestFocus() } }
}

/** The week's total, big, and how it compares with the week before. */
@Composable
private fun Hero(s: WatchStats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Afgelopen 7 dagen", style = MaterialTheme.typography.labelLarge, color = Live)
        Text(WeekSummary.duration(s.weekSeconds), style = MaterialTheme.typography.displaySmall)
        val diff = s.weekSeconds - s.lastWeekSeconds
        val compare = when {
            s.lastWeekSeconds < 60 -> null
            kotlin.math.abs(diff) < 15 * 60 -> "Net zoveel als de week ervoor"
            diff > 0 -> "↑ ${WeekSummary.duration(diff)} meer dan de week ervoor"
            else -> "↓ ${WeekSummary.duration(-diff)} minder dan de week ervoor"
        }
        compare?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
    }
}

/** Sport, films, series…: one bar split by time, the legend underneath. */
@Composable
private fun KindBar(kinds: List<Pair<WatchKind, Int>>) {
    if (kinds.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            kinds.forEachIndexed { i, (_, s) -> Box(Modifier.weight(s.toFloat()).fillMaxHeight().background(Live.copy(alpha = KindShades[i]))) }
        }
        // Three per line: six kinds in one line do not fit next to the calendar.
        kinds.withIndex().chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                line.forEach { (i, kind) ->
                    val (k, s) = kind
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(Live.copy(alpha = KindShades[i]), CircleShape))
                        Text("${k.label} ${WeekSummary.duration(s)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/** Three tiles of things worth knowing, then the favourite and the longest sitting. */
@Composable
private fun Facts(s: WatchStats) {
    val tiles = listOfNotNull(
        s.matches.takeIf { it > 0 }?.let { "$it" to if (it == 1) "wedstrijd" else "wedstrijden" },
        s.usualHour?.let { "%02d:00".format(it) to "je vaste tijd" },
        s.streak.takeIf { it > 1 }?.let { "$it dagen" to "op rij gekeken" },
    )
    if (tiles.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.forEach { (big, small) ->
                Column(Modifier.weight(1f).background(SurfaceColor, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(big, style = MaterialTheme.typography.headlineSmall)
                    Text(small, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        s.favourite?.let { Text("Favoriet: ${it.first}, ${WeekSummary.duration(it.second)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        s.longest?.takeIf { it.second >= 30 * 60 }?.let { Text("Langste zit: ${it.first}, ${WeekSummary.duration(it.second)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
    }
}

/** Five weeks, Monday first, ending with this week. Days to come are there but can't be focused. */
@Composable
private fun Calendar(
    today: LocalDate,
    perDay: Map<LocalDate, Int>,
    todayFocus: FocusRequester,
    onFocusDay: (LocalDate) -> Unit,
    onOpenDay: () -> Unit,
) {
    val start = today.with(DayOfWeek.MONDAY).minusWeeks(CALENDAR_WEEKS - 1L)
    val most = (perDay.filterKeys { !it.isBefore(start) }.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ma", "di", "wo", "do", "vr", "za", "zo").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1f).padding(start = 6.dp))
            }
        }
        repeat(CALENDAR_WEEKS) { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { d ->
                    val day = start.plusDays(w * 7L + d)
                    val cell = Modifier.weight(1f).height(36.dp)
                    if (day.isAfter(today)) {
                        Box(cell.background(SurfaceColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp)))
                    } else {
                        DayCell(
                            day = day,
                            seconds = perDay[day] ?: 0,
                            most = most,
                            isToday = day == today,
                            onFocus = { onFocusDay(day) },
                            onClick = onOpenDay,
                            modifier = cell.then(if (day == today) Modifier.focusRequester(todayFocus) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/** A day: its number top left, a dot that grows with the time watched (square root, so a quiet day still shows). */
@Composable
private fun DayCell(day: LocalDate, seconds: Int, most: Int, isToday: Boolean, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceColor,
            contentColor = if (isToday) Live else TextSecondary,
            focusedContainerColor = FocusBar,
            focusedContentColor = TextOnLight,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 2.dp))
            if (seconds >= 60) {
                val size = 6f + 14f * sqrt(seconds.toFloat() / most)
                val dot = if (LocalContentColor.current == TextOnLight) TextOnLight else Live
                Box(Modifier.align(Alignment.Center).padding(top = 6.dp).size(size.dp).background(dot, CircleShape))
            }
        }
    }
}

private fun kind(r: WatchLogEntity) = when (r.type) {
    ContentType.LIVE -> "Live"
    ContentType.MOVIE -> "Film"
    ContentType.SERIES -> "Serie"
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Vandaag"
    today.minusDays(1) -> "Gisteren"
    else -> date.format(day).replaceFirstChar { it.uppercase() }
}

private fun route(r: WatchLogEntity) = PlayerRoute(
    kind = when (r.type) {
        ContentType.LIVE -> PlayerRoute.Kind.LIVE
        ContentType.MOVIE -> PlayerRoute.Kind.MOVIE
        ContentType.SERIES -> PlayerRoute.Kind.EPISODE
    },
    id = r.itemId,
    title = r.title,
    containerExtension = r.containerExtension,
    categoryId = r.categoryId,
)
