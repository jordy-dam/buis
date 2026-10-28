package nl.jordy.iptv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class SettingsState(
    val server: String = "",
    val username: String = "",
    val lastSync: String? = null,
    val counts: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val c = credentials.current()
            val dao = db.catalog()
            val nl = Locale.forLanguageTag("nl-NL")
            val n = NumberFormat.getIntegerInstance(nl)
            _state.value = SettingsState(
                // Host and port only: the password never shows on screen.
                server = c?.baseUrl?.let { "${it.host}:${it.port}" } ?: "–",
                username = c?.username ?: "–",
                lastSync = db.syncInfo().get()?.lastSyncAt?.let {
                    DateTimeFormatter.ofPattern("d MMMM HH:mm", nl).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
                },
                counts = "${n.format(dao.liveCount())} zenders · ${n.format(dao.movieCount())} films · ${n.format(dao.seriesCount())} series",
            )
        }
    }

    /**
     * Wipes everything tied to this provider: catalog, favourites and resume positions only make
     * sense for its ids, and viewing history shouldn't outlive the login it belongs to.
     */
    fun logout(then: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        library.clearCache()
        epg.clearCache()
        credentials.clear()
        then()
    }
}

/** Phase 1 settings: just what's needed to keep using the app without clearing its data by hand. */
@Composable
fun SettingsScreen(onRefreshCatalog: () -> Unit, onLoggedOut: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text("Instellingen", style = MaterialTheme.typography.headlineMedium)

        Section("Abonnement") {
            Fact("Server", state.server)
            Fact("Gebruiker", state.username)
        }

        Section("Catalogus") {
            Fact("Bijgewerkt", state.lastSync ?: "nog nooit")
            Fact("Inhoud", state.counts)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton("Catalogus vernieuwen", onRefreshCatalog)
            SecondaryButton("Uitloggen", { confirmLogout = true })
        }
    }

    if (confirmLogout) {
        LogoutDialog(onConfirm = { vm.logout(onLoggedOut) }, onDismiss = { confirmLogout = false })
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.width(160.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Uitloggen?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "De catalogus, je favorieten en waar je gebleven was worden van deze tv gewist.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Default focus on cancelling: this wipes data.
                SecondaryButton("Annuleren", onDismiss, Modifier.focusRequester(cancel))
                PrimaryButton("Uitloggen", onConfirm)
            }
        }
    }
    LaunchedEffect(Unit) { cancel.requestFocus() }
}
