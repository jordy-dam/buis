package nl.jordy.iptv.ui.taste

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import nl.jordy.iptv.data.repo.Taste
import nl.jordy.iptv.ui.settings.SettingRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.repo.TasteRepository
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

@HiltViewModel
class TasteProfileViewModel @Inject constructor(private val taste: TasteRepository) : ViewModel() {
    val summary = taste.summary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun reset() = viewModelScope.launch { taste.reset() }
}

/**
 * Instellingen → Mijn smaak: what BUIS took from this profile's thumbs, so you can see why "Voor
 * jou" shows what it shows. Each part is a card with its own icon; directors, actors and the
 * period are buttons that search for them. "Smaak wissen" starts over (asked first; Favorieten stay).
 */
@Composable
fun TasteProfileScreen(onLearn: () -> Unit, onSearch: (String) -> Unit, vm: TasteProfileViewModel = hiltViewModel()) {
    // Null while the genres are counted (a few seconds on a big catalog): the page shows at once anyway.
    val s = vm.summary.collectAsStateWithLifecycle().value
    var confirm by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    if (confirm) ResetDialog(onConfirm = { vm.reset(); confirm = false }, onDismiss = { confirm = false })
    Row(Modifier.fillMaxSize().background(Background).padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Mijn smaak", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Wat BUIS uit je duimen haalt voor Films en Series voor jou. Het staat alleen op deze tv.",
                style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
            )
            if (s != null && !s.empty) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Count(Icons.Filled.ThumbUp, s.likes, "leuk", Modifier.weight(1f))
                    Count(Icons.Filled.ThumbDown, s.dislikes, "niks", Modifier.weight(1f))
                    Count(Icons.Filled.Bookmark, s.wants, "wil ik zien", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                PrimaryButton("Films beoordelen", onLearn, Modifier.focusRequester(first))
                if (s != null && !s.empty) SecondaryButton("Smaak wissen", { confirm = true })
            }
        }
        Column(Modifier.weight(1f)) {
            when {
                s == null -> Text("Even rekenen…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                s.empty -> Text("Zeg van een paar films of je ze leuk vindt, dan staat hier wat BUIS ervan maakt.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                else -> TasteRows(s, onSearch)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** A count of thumbs: its icon, the number big. */
@Composable
private fun Count(icon: ImageVector, n: Int, label: String, modifier: Modifier) {
    Column(modifier.background(SurfaceColor, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Text("$n", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
    }
}

/**
 * The taste as Instellingen shows things: a caption per group, a row per part with its icon. What
 * you can search for is a row you can press, its value in blue like every value you can act on.
 */
@Composable
private fun TasteRows(s: Taste.Summary, onSearch: (String) -> Unit) {
    // Someone who directs and acts gets one row.
    val people = (s.directors.map { it to "Regie" } + s.actors.map { it to "Speelt mee" })
        .groupBy({ it.first }, { it.second })
        .map { (name, roles) -> name to if (roles.size > 1) "Regie en spel" else roles.single() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Caption("Wat je kijkt", top = false) }
        if (s.genres.isNotEmpty()) item { SettingRow("Vindt leuk", s.genres.joinToString(", "), leading = Icons.Filled.Favorite) }
        if (s.avoid.isNotEmpty()) item { SettingRow("Liever niet", s.avoid.joinToString(", "), leading = Icons.Filled.Block) }
        s.period?.let { p -> item { SettingRow("Periode", value = p, onClick = { onSearch(p) }, leading = Icons.Filled.CalendarMonth) } }
        s.length?.let { l -> item { SettingRow("Lengte", value = l, leading = Icons.Filled.Timer) } }
        if (people.isNotEmpty()) {
            item { Caption("Regisseurs en acteurs") }
            items(people, key = { it.first }) { (name, role) ->
                SettingRow(name, value = role, onClick = { onSearch(name) }, leading = if (role == "Speelt mee") Icons.Filled.TheaterComedy else Icons.Filled.Videocam)
            }
            item {
                Text("OK zoekt hun films.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun Caption(text: String, top: Boolean = true) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(start = 16.dp, top = if (top) 16.dp else 0.dp, bottom = 4.dp))

/** Focus starts on Annuleren: wiping all thumbs shouldn't be one OK away. */
@Composable
private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Smaak wissen?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Alle duimen van dit profiel gaan weg, en Voor jou begint opnieuw. Films en series bij Favorieten blijven staan.",
                style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton("Annuleren", onDismiss, Modifier.focusRequester(cancel))
                PrimaryButton("Wissen", onConfirm)
            }
        }
    }
    LaunchedEffect(Unit) { cancel.requestFocus() }
}
