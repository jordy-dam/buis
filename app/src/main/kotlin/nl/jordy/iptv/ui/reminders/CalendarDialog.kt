package nl.jordy.iptv.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.CalendarEntity
import nl.jordy.iptv.data.db.CalendarNext
import nl.jordy.iptv.data.db.ChannelPick
import nl.jordy.iptv.data.repo.CalendarPreset
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.IconButton
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.home.whenLabel
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary

/** Where the dialog is: the list, picking a calendar, or picking its channel. */
private sealed interface Step {
    data object List : Step
    data class Source(val error: String? = null) : Step
    /** A new calendar ([url] set) or another channel for [editing]. */
    data class Channel(val url: String?, val name: String?, val query: String, val editing: CalendarEntity? = null) : Step
}

/**
 * Agenda's in Volgen: the calendars this profile follows, each on its own channel. Adding is two
 * steps: which calendar (a ready one, no typing, or a link), then which channel. OK on a calendar
 * picks another channel; the cross takes it away.
 */
@Composable
internal fun CalendarDialog(
    calendars: List<CalendarEntity>,
    next: List<CalendarNext>,
    presets: List<CalendarPreset>,
    searchChannels: suspend (String) -> List<ChannelPick>,
    onAdd: suspend (url: String, name: String?, channel: ChannelPick) -> Result<Unit>,
    onSetChannel: (CalendarEntity, ChannelPick) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf<Step>(Step.List) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember(step) { FocusRequester() }
    // Down from the list goes to "Agenda toevoegen", not to whichever button is nearest (Klaar).
    val add = remember { FocusRequester() }

    fun pick(s: Step.Channel, channel: ChannelPick) {
        val editing = s.editing
        if (editing != null) {
            onSetChannel(editing, channel)
            step = Step.List
            return
        }
        busy = true
        scope.launch {
            val result = onAdd(s.url!!, s.name, channel)
            busy = false
            step = when (result.exceptionOrNull()) {
                null -> Step.List
                // A clock that's far off fails every secure connection; the link itself is fine then.
                is javax.net.ssl.SSLException -> Step.Source("Geen veilige verbinding met deze agenda. Staat de klok van de tv goed?")
                else -> Step.Source("Deze agenda kon niet worden opgehaald. Klopt de link?")
            }
        }
    }

    Dialog(onDismissRequest = { if (step == Step.List) onDismiss() else step = Step.List }) {
        Column(
            Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(24.dp).width(560.dp).heightIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = step) {
                Step.List -> {
                    Text("Agenda's", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Voor sport zonder gids, zoals Viaplay F1 of F1 TV Pro. Elke afspraak in de agenda geeft een melding op de zender die je kiest.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(calendars, key = { it.url }) { c ->
                            val n = next.firstOrNull { it.url == c.url }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TwoLineRow(
                                    "${c.name} · ${c.channelName}",
                                    n?.let { "${whenLabel(it.start)} · ${it.title}" } ?: "Geen afspraken meer in deze agenda",
                                    onClick = { step = Step.Channel(null, c.name, c.channelName, editing = c) },
                                    modifier = Modifier.weight(1f).focusProperties { down = add }.then(if (c == calendars.first()) Modifier.focusRequester(first) else Modifier),
                                )
                                IconButton(Icons.Filled.Close, "Weghalen", { onRemove(c.url) }, Modifier.focusProperties { down = add })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PrimaryButton("Agenda toevoegen", { step = Step.Source() }, Modifier.focusRequester(add).then(if (calendars.isEmpty()) Modifier.focusRequester(first) else Modifier))
                        SecondaryButton("Klaar", onDismiss)
                    }
                }
                is Step.Source -> {
                    var link by remember { mutableStateOf("") }
                    Text("Welke agenda?", style = MaterialTheme.typography.headlineMedium)
                    s.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80)) }
                    // Ready calendars by sport, then a link of your own at the bottom.
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        presets.groupBy { it.group }.forEach { (group, list) ->
                            item(key = group) { Text(group, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(start = 14.dp, top = 8.dp)) }
                            items(list, key = { it.url }) { p ->
                                val followed = calendars.any { it.url == p.url }
                                TwoLineRow(
                                    p.label,
                                    if (followed) "Volg je al" else p.url.substringAfter("://").substringBefore('/'),
                                    onClick = { step = Step.Channel(p.url, p.name, p.channelQuery) },
                                    modifier = if (p == presets.first()) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                    TvTextField(
                        label = "Of een eigen link (https of webcal)",
                        value = link,
                        onValueChange = { link = it },
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (link.isNotBlank()) step = Step.Channel(link, null, "") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (link.isNotBlank()) PrimaryButton("Verder", { step = Step.Channel(link, null, "") })
                        SecondaryButton("Terug", { step = Step.List })
                    }
                }
                is Step.Channel -> {
                    var query by remember { mutableStateOf(s.query) }
                    var results by remember { mutableStateOf<List<ChannelPick>?>(null) }
                    // A short pause after typing, so every letter doesn't run a search.
                    LaunchedEffect(query) {
                        delay(250)
                        results = searchChannels(query)
                    }
                    Text("Op welke zender?", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (busy) "Agenda ophalen…" else (s.editing?.let { "${it.name}, nu op ${it.channelName}" } ?: s.name ?: "Eigen agenda"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    TvTextField(
                        label = "Zoek zender",
                        value = query,
                        onValueChange = { query = it },
                        modifier = if (s.query.isEmpty()) Modifier.focusRequester(first) else Modifier,
                        imeAction = ImeAction.Search,
                    )
                    val list = results.orEmpty()
                    if (results != null && list.isEmpty()) {
                        Text(if (query.isBlank()) "Typ een deel van de zendernaam." else "Geen zender met \"$query\".", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list, key = { "${it.categoryId}:${it.streamId}" }) { c ->
                            // Two streams with one name in one category ("VIAPLAY FORMULE 1" twice): tell them apart.
                            val same = list.filter { it.name == c.name && it.categoryId == c.categoryId }
                            TwoLineRow(
                                c.name,
                                c.categoryName + if (same.size > 1) " · versie ${same.indexOf(c) + 1}" else "",
                                onClick = { if (!busy) pick(s, c) },
                                modifier = if (s.query.isNotEmpty() && c == list.first()) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                    SecondaryButton("Terug", { step = if (s.editing != null) Step.List else Step.Source() })
                    // A suggested search lands on its first channel once it's there.
                    LaunchedEffect(list.isNotEmpty()) { if (s.query.isNotEmpty() && list.isNotEmpty()) runCatching { first.requestFocus() } }
                }
            }
        }
    }
    LaunchedEffect(step) { runCatching { first.requestFocus() } }
}

@Composable
private fun TwoLineRow(title: String, line: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = Focus.bar(container = Color.Transparent),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
