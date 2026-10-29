package nl.jordy.iptv.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.epg.EpgGuide
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.sync.CatalogSync
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

data class SyncState(
    val counts: Map<ContentType, Int> = emptyMap(),
    val current: ContentType? = null,
    val error: String? = null,
    val done: Boolean = false,
    /** A catalog from an earlier sync exists, so a failed refresh can be skipped. */
    val canSkip: Boolean = false,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val sync: CatalogSync,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val guide: EpgGuide,
) : ViewModel() {
    private val _state = MutableStateFlow(SyncState())
    val state = _state.asStateFlow()

    init { start() }

    fun start() {
        _state.value = SyncState()
        viewModelScope.launch {
            // After a logout wipe there is no profile until here: the shell needs one.
            profiles.ensureOne()
            val c = credentials.current() ?: return@launch _state.update { it.copy(error = "Niet ingelogd.") }
            try {
                sync.run(c) { p -> _state.update { it.copy(current = p.type, counts = it.counts + (p.type to p.items)) } }
                _state.update { it.copy(done = true, current = null) }
                // New channels may have new EPG ids: rebuild the guide with them.
                guide.refresh()
            } catch (e: Exception) {
                val hasCatalog = db.syncInfo().get() != null
                _state.update { it.copy(error = "Ophalen mislukt: ${e.message ?: e.javaClass.simpleName}", current = null, canSkip = hasCatalog) }
            }
        }
    }

    fun logout(then: () -> Unit) = viewModelScope.launch { credentials.clear(); then() }
}

@Composable
fun SyncScreen(onDone: () -> Unit, onLogout: () -> Unit, vm: SyncViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val retry = remember { FocusRequester() }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    LaunchedEffect(state.error) { if (state.error != null) retry.requestFocus() }

    val fmt = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("nl-NL")) }
    Box(Modifier.fillMaxSize().padding(SafeArea), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Catalogus ophalen", style = MaterialTheme.typography.displaySmall)
            Text("Dit gebeurt na het inloggen en daarna eens per dag.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            listOf(ContentType.LIVE to "Zenders", ContentType.MOVIE to "Films", ContentType.SERIES to "Series").forEach { (type, label) ->
                val count = state.counts[type]
                val active = state.current == type
                Row(Modifier.padding(top = 4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(200.dp), color = if (active) TextPrimary else TextSecondary)
                    Text(
                        when {
                            count == null -> "…"
                            active -> "${fmt.format(count)} …"
                            else -> fmt.format(count)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary,
                    )
                }
            }
            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrimaryButton("Opnieuw", vm::start, Modifier.focusRequester(retry))
                    if (state.canSkip) SecondaryButton("Oude catalogus gebruiken", onDone)
                    SecondaryButton("Uitloggen", { vm.logout(onLogout) })
                }
            }
        }
    }
}
