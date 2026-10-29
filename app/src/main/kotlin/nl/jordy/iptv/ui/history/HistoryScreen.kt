package nl.jordy.iptv.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.data.db.WatchLogEntity
import nl.jordy.iptv.data.repo.WatchLog
import nl.jordy.iptv.data.repo.WeekSummary
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.settings.SettingRow
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(watchLog: WatchLog) : ViewModel() {
    /** The last 30 days; the weekoverzicht takes the last 7 of them. */
    val rows = watchLog.recent(days = 30).stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val date = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl"))

/**
 * Kijkgeschiedenis: the weekoverzicht on the left, everything watched on the right, per day.
 * OK on a row plays it again; Back goes back to Instellingen.
 */
@Composable
fun HistoryScreen(onPlay: (PlayerRoute) -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val list = rows ?: return
    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
    val week = remember(list) { WeekSummary.of(list.filter { it.startedAt >= weekAgo }) }
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().background(Background).padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kijkgeschiedenis", style = MaterialTheme.typography.headlineMedium)
            WeekPanel(week)
        }
        if (list.isEmpty()) {
            Text(
                "Nog niets gekeken. Alles wat je langer dan een minuut kijkt, komt hier te staan.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 56.dp),
            )
            return@Row
        }
        val days = list.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.entries.forEachIndexed { d, (day, entries) ->
                item(key = "d$day") {
                    Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = if (d == 0) 8.dp else 16.dp, bottom = 4.dp))
                }
                items(entries, key = { it.id }) { r ->
                    SettingRow(
                        r.title,
                        listOfNotNull(time.format(Instant.ofEpochMilli(r.startedAt)), kind(r), r.detail).joinToString(" · "),
                        value = WeekSummary.duration(r.seconds),
                        onClick = { onPlay(route(r)) },
                        modifier = if (d == 0 && r == entries.first()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun WeekPanel(w: WeekSummary) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Afgelopen 7 dagen", style = MaterialTheme.typography.labelLarge, color = Live)
        Text(WeekSummary.duration(w.totalSeconds), style = MaterialTheme.typography.displaySmall)
        listOf("Live" to w.liveSeconds, "Films" to w.filmSeconds, "Series" to w.seriesSeconds)
            .filter { it.second >= 60 }
            .takeIf { it.isNotEmpty() }
            ?.let { parts -> Line(parts.joinToString(" · ") { "${it.first} ${WeekSummary.duration(it.second)}" }) }
        if (w.films + w.episodes > 0) Line("${w.films} ${if (w.films == 1) "film" else "films"} · ${w.episodes} ${if (w.episodes == 1) "aflevering" else "afleveringen"}")
        if (w.topChannels.isNotEmpty()) {
            Line("Meest gekeken: " + w.topChannels.joinToString(", ") { "${it.first} (${WeekSummary.duration(it.second)})" })
        }
        w.longestBinge?.takeIf { it.second >= 60 * 60 }?.let { Line("Langste binge: ${it.first}, ${WeekSummary.duration(it.second)}") }
        w.busiestDay?.let { Line("Drukste dag: ${WeekSummary.dayName(it.first)}, ${WeekSummary.duration(it.second)}") }
    }
}

@Composable
private fun Line(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

private fun kind(r: WatchLogEntity) = when (r.type) {
    ContentType.LIVE -> "Live"
    ContentType.MOVIE -> "Film"
    ContentType.SERIES -> "Serie"
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Vandaag"
        today.minusDays(1) -> "Gisteren"
        else -> date.format(day).replaceFirstChar { it.uppercase() }
    }
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
