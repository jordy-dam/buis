package nl.jordy.iptv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.BuildConfig
import nl.jordy.iptv.data.epg.XmltvProbe
import nl.jordy.iptv.data.settings.CountryFilter
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.UserPrefs
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.ListItemSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
import nl.jordy.iptv.ui.theme.Accent
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
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
    private val profiles: ProfileRepository,
    private val xmltvProbe: XmltvProbe,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    val prefs = profiles.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, UserPrefs())
    val profileName = profiles.current.map { it?.name.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Countries this provider actually has, for the picker. */
    val countries = MutableStateFlow<List<String>>(emptyList())

    fun toggleCountry(code: String) = viewModelScope.launch {
        val now = prefs.value.preferredCountries
        // Never empty: with no preferred country the filter would show nothing useful.
        val next = if (code in now) (now - code).ifEmpty { now } else now + code
        profiles.setPreferredCountries(next)
    }

    /** Debug builds only: how much of the provider's full guide covers the preferred channels. */
    val epgCheck = MutableStateFlow<String?>(null)

    fun checkEpg() = viewModelScope.launch {
        epgCheck.value = "Gids ophalen…"
        epgCheck.value = xmltvProbe.run(prefs.value.preferredCountries).also { android.util.Log.i("EpgCheck", it) }
    }

    fun toggleOnlyPreferred() = viewModelScope.launch { profiles.setOnlyPreferred(!prefs.value.onlyPreferred) }

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
            countries.value = CountryFilter.countries(dao.allCategories(), profiles.currentPrefs().preferredCountries)
        }
    }

    /**
     * Wipes everything tied to this provider: catalog, profiles, favourites and resume positions
     * only make sense for its ids, and viewing history shouldn't outlive the login it belongs to.
     * The next sync creates a fresh first profile.
     */
    fun logout(then: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        library.clearCache()
        epg.clearCache()
        credentials.clear()
        then()
    }
}

/**
 * Settings, split by whom they affect: the watching profile's countries, and the household's
 * catalog and subscription. Profiles themselves are managed from "Wie kijkt er?".
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(onRefreshCatalog: () -> Unit, onLoggedOut: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val profileName by vm.profileName.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val epgCheck by vm.epgCheck.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var pickCountries by remember { mutableStateOf(false) }

    // Entering from the rail (whose Instellingen item sits at the bottom) must land on the first
    // setting, not on whatever row is nearest: that would be Uitloggen.
    val first = remember { FocusRequester() }
    Column(
        Modifier.width(780.dp).focusRestorer(first).focusGroup().verticalScroll(rememberScrollState()).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Instellingen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))

        Header("Voor $profileName")
        SettingRow(
            "Voorkeurslanden",
            "Staan bovenaan in Live TV, Films, Series en Zoeken.",
            value = prefs.preferredCountries.sorted().joinToString(", ") { NameParser.countryName(it) },
            onClick = { pickCountries = true },
            modifier = Modifier.focusRequester(first),
        )
        SettingRow(
            "Alleen voorkeurslanden tonen",
            "Andere landen blijven bereikbaar via \"Alle landen tonen\" onderaan elke lijst.",
            value = if (prefs.onlyPreferred) "Aan" else "Uit",
            onClick = vm::toggleOnlyPreferred,
        )

        Header("Voor iedereen")
        SettingRow(
            "Catalogus vernieuwen",
            state.lastSync?.let { "Bijgewerkt $it · ${state.counts}" } ?: "Nog nooit opgehaald.",
            onClick = onRefreshCatalog,
        )

        SettingRow("Server", value = state.server)
        SettingRow("Gebruiker", value = state.username)
        SettingRow(
            "Uitloggen",
            "Wist de catalogus en alle profielen, met hun favorieten en kijkgeschiedenis, op deze tv.",
            onClick = { confirmLogout = true },
            destructive = true,
        )

        if (BuildConfig.DEBUG) {
            Header("Ontwikkeling")
            SettingRow("EPG controleren", epgCheck ?: "Hoeveel van je voorkeurszenders een programmagids hebben.", onClick = vm::checkEpg)
        }
    }

    if (pickCountries) {
        CountryDialog(countries, prefs.preferredCountries, vm::toggleCountry, onDismiss = { pickCountries = false })
    }
    if (confirmLogout) {
        LogoutDialog(onConfirm = { vm.logout(onLoggedOut) }, onDismiss = { confirmLogout = false })
    }
}

@Composable
internal fun Header(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 2.dp),
    )
}

/**
 * One setting, Google TV style: name and explanation on the left, current value on the right.
 * Rows without [onClick] only inform, so they aren't focusable and the d-pad skips them.
 */
@Composable
internal fun SettingRow(
    title: String,
    description: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (destructive) Danger else MaterialTheme.colorScheme.onSurface)
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            }
            value?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = if (onClick != null) Accent else TextSecondary, modifier = Modifier.padding(start = 24.dp)) }
        }
    }
    if (onClick == null) {
        content()
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Focus.border),
        colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceColor, focusedContainerColor = SurfaceRaised),
    ) { content() }
}

private val Danger = androidx.compose.ui.graphics.Color(0xFFFF8A80)

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
                "De catalogus en alle profielen, met hun favorieten en kijkgeschiedenis, worden van deze tv gewist.",
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

@Composable
private fun CountryDialog(countries: List<String>, selected: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(24.dp).width(420.dp).heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Voorkeurslanden", style = MaterialTheme.typography.headlineMedium)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(countries) { i, code ->
                    ListItemSurface(
                        text = (if (code in selected) "✓  " else "     ") + NameParser.countryName(code),
                        selected = code in selected,
                        onClick = { onToggle(code) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            PrimaryButton("Klaar", onDismiss)
        }
    }
    LaunchedEffect(countries.isNotEmpty()) { if (countries.isNotEmpty()) runCatching { first.requestFocus() } }
}
