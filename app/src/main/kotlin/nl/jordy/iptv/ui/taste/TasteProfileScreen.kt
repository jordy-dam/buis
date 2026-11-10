package nl.jordy.iptv.ui.taste

import androidx.compose.foundation.background
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
import nl.jordy.iptv.ui.theme.Live
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
 * Instellingen → Mijn smaak: what BUIS took from this profile's thumbs, in words, so you can see why
 * "Voor jou" shows what it shows. "Smaak wissen" starts over (asked first; Favorieten stay).
 */
@Composable
fun TasteProfileScreen(onLearn: () -> Unit, vm: TasteProfileViewModel = hiltViewModel()) {
    // Null while the genres are counted (a few seconds on a big catalog): the page shows at once anyway.
    val s = vm.summary.collectAsStateWithLifecycle().value
    var confirm by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    if (confirm) ResetDialog(onConfirm = { vm.reset(); confirm = false }, onDismiss = { confirm = false })
    Row(Modifier.fillMaxSize().background(Background).padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mijn smaak", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Wat BUIS uit je duimen haalt voor Films en Series voor jou. Het staat alleen op deze tv.",
                style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
            )
            Text(
                when {
                    s == null -> " "
                    s.empty -> "Nog geen duimen."
                    else -> "${s.likes} leuk · ${s.dislikes} niks · ${s.wants} wil ik zien"
                },
                style = MaterialTheme.typography.titleMedium, color = Live, modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                PrimaryButton("Films beoordelen", onLearn, Modifier.focusRequester(first))
                if (s != null && !s.empty) SecondaryButton("Smaak wissen", { confirm = true })
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s == null) {
                Text("Even rekenen…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            } else if (s.empty) {
                Text("Zeg van een paar films of je ze leuk vindt, dan staat hier wat BUIS ervan maakt.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            } else {
                Line("Vindt leuk", s.genres)
                Line("Liever niet", s.avoid)
                Line("Regisseurs", s.directors)
                Line("Acteurs", s.actors)
                Line("Periode", listOfNotNull(s.period))
                Line("Lengte", listOfNotNull(s.length))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** One part of the taste; left out when there's nothing to say yet. */
@Composable
private fun Line(label: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Text(values.joinToString(", "), style = MaterialTheme.typography.titleMedium)
    }
}

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
