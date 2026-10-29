package nl.jordy.iptv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.repo.ChannelAdvice
import nl.jordy.iptv.data.repo.ChannelHealthRepository
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

@HiltViewModel
class ChannelHealthViewModel @Inject constructor(private val repo: ChannelHealthRepository) : ViewModel() {
    /** Proposals are worked out again whenever a replacement changes, so a fixed channel drops off. */
    val proposals = repo.swaps.map { swaps ->
        val replaced = swaps.map { it.fromStreamId }.toSet()
        runCatching { repo.proposals() }.getOrDefault(emptyList()).filter { it.channel.streamId !in replaced }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Replacements in place, as labels: from → to. */
    val swaps = repo.swaps.map { swaps ->
        val names = repo.channels(swaps.flatMap { listOf(it.fromStreamId, it.toStreamId) }).associateBy { it.streamId }
        swaps.map { s -> Triple(s.fromStreamId, names[s.fromStreamId]?.let(ChannelAdvice::label) ?: "Zender ${s.fromStreamId}", names[s.toStreamId]?.let(ChannelAdvice::label) ?: "Zender ${s.toStreamId}") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun accept(p: ChannelAdvice.Proposal) {
        val to = p.sibling ?: return
        viewModelScope.launch { repo.swap(p.channel.streamId, to.streamId) }
    }

    fun undo(from: Int) = viewModelScope.launch { repo.undo(from) }
}

/**
 * Zenderstatus: channels that kept hiccuping, each with what to do about it, and the replacements
 * already chosen. Nothing changes without OK; OK on a replacement puts the original back.
 */
@Composable
fun ChannelHealthScreen(vm: ChannelHealthViewModel = hiltViewModel()) {
    val proposals by vm.proposals.collectAsStateWithLifecycle()
    val swaps by vm.swaps.collectAsStateWithLifecycle()
    val list = proposals ?: return
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(Background).padding(SafeArea), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Zenderstatus", style = MaterialTheme.typography.headlineMedium)
        Text(
            "BUIS houdt bij welke zenders haperen of stoppen. Haperde een zender minstens de helft van de keren, dan staat hij hier met een voorstel.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(900.dp),
        )
        LazyColumn(Modifier.width(900.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (list.isEmpty() && swaps.isEmpty()) {
                item { Text("Geen haperende zenders gezien. Alles loopt.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
            }
            items(list, key = { "p${it.channel.streamId}" }) { p ->
                val sibling = p.sibling
                val why = "${p.stats.troubled} van de ${p.stats.sessions} keer haperde of stopte hij."
                val what = when {
                    sibling == null -> "Geen andere versie van deze zender gevonden."
                    p.siblingStats != null -> "${ChannelAdvice.label(sibling)} liep ${p.siblingStats.sessions} keer goed."
                    else -> "Er is ook ${ChannelAdvice.label(sibling)}; die is nog niet geprobeerd."
                }
                SettingRow(
                    "${ChannelAdvice.label(p.channel)} hapert vaak",
                    "$why $what",
                    value = sibling?.let { if (p.siblingStats != null) "Gebruik deze" else "Probeer deze" },
                    onClick = sibling?.let { { vm.accept(p) } },
                    modifier = if (p == list.firstOrNull { it.sibling != null }) Modifier.focusRequester(first) else Modifier,
                )
            }
            if (swaps.isNotEmpty()) {
                item { Text("Vervangen", style = MaterialTheme.typography.labelLarge, color = Live, modifier = Modifier.padding(top = 16.dp)) }
                items(swaps, key = { "s${it.first}" }) { (from, fromName, toName) ->
                    SettingRow(
                        fromName,
                        "Speelt $toName. Favorieten en zappen gaan vanzelf mee.",
                        value = "Terugzetten",
                        onClick = { vm.undo(from) },
                        modifier = if (list.none { it.sibling != null } && from == swaps.first().first) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
    LaunchedEffect(list.size, swaps.size) { runCatching { first.requestFocus() } }
}
