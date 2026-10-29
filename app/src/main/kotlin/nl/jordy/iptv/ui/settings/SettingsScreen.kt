package nl.jordy.iptv.ui.settings

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.BuildConfig
import nl.jordy.iptv.data.epg.EpgGuide
import nl.jordy.iptv.data.epg.XmltvProbe
import nl.jordy.iptv.data.settings.CountryFilter
import nl.jordy.iptv.data.profile.Pin
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.player.BreakTimer
import nl.jordy.iptv.player.SleepTimer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.window.DialogProperties
import nl.jordy.iptv.ui.dream.DreamScreen
import nl.jordy.iptv.ui.dream.DreamPosters
import nl.jordy.iptv.player.TrackLabels
import nl.jordy.iptv.data.profile.Bedtime
import nl.jordy.iptv.ui.profiles.PinDialog
import androidx.compose.runtime.rememberCoroutineScope
import nl.jordy.iptv.data.settings.UserPrefs
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.ListItemSurface
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.data.repo.FollowRepository
import nl.jordy.iptv.data.repo.FollowMatch
import nl.jordy.iptv.ui.reminders.FollowDialog
import nl.jordy.iptv.data.auth.SubscriptionData
import kotlinx.coroutines.flow.mapLatest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.repo.MovieEnricher
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.theme.onLight
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.BrandText
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.components.AvatarCircle
import nl.jordy.iptv.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
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
    private val subscription: SubscriptionData,
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val epg: EpgRepository,
    private val guide: EpgGuide,
    enricher: MovieEnricher,
    private val follows: FollowRepository,
    private val posters: DreamPosters,
) : ViewModel() {
    /** Debug builds: the screensaver's posters, for its preview. */
    suspend fun dreamPosters() = posters.load()

    val followTerms = follows.terms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Per term what the guide has next, so a term that finds nothing shows it right away. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val followNext = follows.terms.mapLatest { terms ->
        val found = runCatching { follows.upcoming() }.getOrDefault(emptyList())
        terms.associateWith { t -> found.firstOrNull { it.term == t } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun follow(term: String) = viewModelScope.launch { follows.add(term) }
    fun unfollow(term: String) = viewModelScope.launch { follows.remove(term) }

    /** "1.234 van 19.826 films": how far the background fetch of film details is. */
    val filmInfo = enricher.progress.map { (done, total) ->
        val n = NumberFormat.getIntegerInstance(Locale.forLanguageTag("nl-NL"))
        if (total == 0) "Geen films in je voorkeurslanden." else "${n.format(done)} van ${n.format(total)} films" + if (done >= total) "" else " · wordt op de achtergrond aangevuld"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    val prefs = profiles.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, UserPrefs())
    val profileName = profiles.current.map { it?.name.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val profile = profiles.current.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The watching profile's id and the PIN checks before it may be edited (see [ProfileRepository.editGates]). */
    suspend fun editGates(): Pair<Long, List<List<String>>> {
        val all = profiles.all()
        val me = all.first { it.id == profiles.currentId() }
        return me.id to ProfileRepository.editGates(me, all)
    }

    /** "Bijgewerkt 21:04 · 528 zenders", or what's going on with the guide right now. */
    val guideStatus = combine(guide.status, guide.info) { status, info ->
        when (status) {
            EpgGuide.Status.Running -> "Gids ophalen…"
            is EpgGuide.Status.Failed -> "Ophalen mislukt (${status.reason}). Probeer het later opnieuw."
            EpgGuide.Status.Idle -> info?.let {
                val nl = Locale.forLanguageTag("nl-NL")
                val at = DateTimeFormatter.ofPattern("d MMMM HH:mm", nl).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it.refreshedAt))
                "Bijgewerkt $at · ${NumberFormat.getIntegerInstance(nl).format(it.streams)} zenders met gids"
            } ?: "Nog niet opgehaald."
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun refreshGuide() = guide.refresh()

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
    fun cycleSleepTimer() = viewModelScope.launch { profiles.setSleepMinutes(SleepTimer.nextChoice(prefs.value.sleepMinutes)) }
    fun cycleBreak() = viewModelScope.launch {
        // Debug builds add a 1-minute choice, to see the break without watching for an hour.
        val choices = if (BuildConfig.DEBUG) BreakTimer.DEBUG_CHOICES else BreakTimer.CHOICES
        profiles.setBreakMinutes(BreakTimer.nextChoice(prefs.value.breakMinutes, choices))
    }
    fun cycleAudioLang() = viewModelScope.launch { profiles.setAudioLang(TrackLabels.next(prefs.value.audioLang, TrackLabels.AUDIO_CHOICES)) }
    fun cycleSubtitleLang() = viewModelScope.launch { profiles.setSubtitleLang(TrackLabels.next(prefs.value.subtitleLang, TrackLabels.SUBTITLE_CHOICES)) }
    fun cycleBedtime() = viewModelScope.launch { profiles.setBedtime(Bedtime.nextTime(prefs.value.bedtime)) }
    fun cycleBedtimeDays() = viewModelScope.launch { profiles.setBedtimeDays(Bedtime.nextDays(prefs.value.bedtimeDays)) }
    fun cycleBreakLength() = viewModelScope.launch {
        profiles.setBreakLength(BreakTimer.nextChoice(prefs.value.breakLength, BreakTimer.LENGTHS))
    }

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
    /** Grown-up PINs that guard "Alles wissen" once a kids profile exists (same rule as managing profiles). */
    suspend fun wipeLock(): List<String> = ProfileRepository.managementLock(profiles.all())

    fun wipeAll(then: () -> Unit) = viewModelScope.launch {
        subscription.wipeAll()
        library.clearCache()
        epg.clearCache()
        credentials.clear()
        then()
    }

    /** Forgets the subscription and the provider's catalog; profiles, history and film details stay (see [SubscriptionData]). */
    fun logout(then: () -> Unit) = viewModelScope.launch {
        subscription.onLogout(credentials.current())
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
fun SettingsScreen(onRefreshCatalog: () -> Unit, onLoggedOut: () -> Unit, onEditProfile: (Long) -> Unit, onOpenHistory: () -> Unit, onOpenChannelHealth: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    // PIN checks still to pass before "Profiel bewerken" opens, as in "Wie kijkt er?".
    var gates by remember { mutableStateOf<Pair<Long, List<List<String>>>?>(null) }
    var dreamPreview by remember { mutableStateOf(false) }
    fun openEdit() = scope.launch {
        val (id, g) = vm.editGates()
        if (g.isEmpty()) onEditProfile(id) else gates = id to g
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val profileName by vm.profileName.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val epgCheck by vm.epgCheck.collectAsStateWithLifecycle()
    val guideStatus by vm.guideStatus.collectAsStateWithLifecycle()
    val filmInfo by vm.filmInfo.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var wipePin by remember { mutableStateOf<List<String>?>(null) }
    var pickCountries by remember { mutableStateOf(false) }
    var editFollows by remember { mutableStateOf(false) }
    val followTerms by vm.followTerms.collectAsStateWithLifecycle()
    val followNext by vm.followNext.collectAsStateWithLifecycle()

    // Entering from the rail (whose Instellingen item sits at the bottom) must land on the first
    // setting, not on whatever row is nearest: that would be Uitloggen.
    val first = remember { FocusRequester() }
    Column(
        Modifier.width(780.dp).focusRestorer(first).focusGroup().verticalScroll(rememberScrollState()).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Instellingen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))

        Header("Voor $profileName") { profile?.let { AvatarCircle(it.name, it.avatar, 28.dp, face = it.face) } }
        SettingRow(
            "Profiel bewerken",
            leading = Icons.Filled.Person,
            onClick = { openEdit() },
            modifier = Modifier.focusRequester(first),
        )
        SettingRow(
            "Voorkeurslanden",
            leading = Icons.Filled.Public,
            value = prefs.preferredCountries.sorted().joinToString(", ") { NameParser.countryName(it) },
            onClick = { pickCountries = true },
        )
        SettingRow(
            "Alleen voorkeurslanden",
            leading = Icons.Filled.FilterAlt,
            toggle = prefs.onlyPreferred,
            onClick = vm::toggleOnlyPreferred,
        )
        SettingRow(
            "Geluid",
            leading = Icons.Filled.Audiotrack,
            value = TrackLabels.name(prefs.audioLang, TrackLabels.AUDIO_CHOICES),
            onClick = vm::cycleAudioLang,
        )
        SettingRow(
            "Ondertitels",
            leading = Icons.Filled.Subtitles,
            value = TrackLabels.name(prefs.subtitleLang, TrackLabels.SUBTITLE_CHOICES),
            onClick = vm::cycleSubtitleLang,
        )
        SettingRow(
            "Slaaptimer",
            leading = Icons.Filled.Snooze,
            value = if (prefs.sleepMinutes == 0) "Uit" else "${prefs.sleepMinutes} min",
            onClick = vm::cycleSleepTimer,
        )
        SettingRow(
            "Bedtijd",
            leading = Icons.Filled.Bedtime,
            value = Bedtime.timeName(prefs.bedtime),
            onClick = vm::cycleBedtime,
        )
        if (prefs.bedtime >= 0) {
            SettingRow(
                "Bedtijd op",
                leading = Icons.Filled.EventRepeat,
                value = Bedtime.daysName(prefs.bedtimeDays),
                onClick = vm::cycleBedtimeDays,
            )
        }
        SettingRow(
            "Pauzemoment",
            leading = Icons.Filled.LocalCafe,
            value = if (prefs.breakMinutes == 0) "Uit" else "${prefs.breakMinutes} min",
            onClick = vm::cycleBreak,
        )
        if (prefs.breakMinutes > 0) {
            SettingRow(
                "Pauzeduur",
                leading = Icons.Filled.HourglassEmpty,
                value = "${prefs.breakLength} min",
                onClick = vm::cycleBreakLength,
            )
        }
        SettingRow(
            "Volgen",
            leading = Icons.Filled.Visibility,
            value = followTerms.joinToString(", ").ifEmpty { "Niets" },
            onClick = { editFollows = true },
        )
        SettingRow(
            "Kijkgeschiedenis",
            leading = Icons.Filled.History,
            onClick = onOpenHistory,
        )

        Header("Voor iedereen") {
            Image(painterResource(R.drawable.ic_buis), contentDescription = null, modifier = Modifier.size(28.dp))
        }
        SettingRow(
            "Catalogus vernieuwen",
            state.lastSync?.let { "Bijgewerkt $it · ${state.counts}" } ?: "Nog nooit opgehaald.",
            leading = Icons.Filled.Refresh,
            onClick = onRefreshCatalog,
        )
        SettingRow("Gids vernieuwen", guideStatus, onClick = vm::refreshGuide, leading = Icons.Filled.EventNote)
        SettingRow("Filmgegevens", value = filmInfo, leading = Icons.Filled.Movie)
        SettingRow("Zenderstatus", onClick = onOpenChannelHealth, leading = Icons.Filled.NetworkCheck)

        SettingRow("Versie", value = "${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_DATE}", leading = Icons.Filled.Info)
        SettingRow("Server", value = state.server, leading = Icons.Filled.Dns)
        SettingRow("Gebruiker", value = state.username, leading = Icons.Filled.AccountCircle)
        SettingRow(
            "Uitloggen",
            leading = Icons.Filled.Logout,
            onClick = { confirmLogout = true },
            destructive = true,
        )
        SettingRow(
            "Alles wissen",
            leading = Icons.Filled.DeleteForever,
            onClick = {
                scope.launch {
                    val lock = vm.wipeLock()
                    if (lock.isEmpty()) confirmWipe = true else wipePin = lock
                }
            },
            destructive = true,
        )

        if (BuildConfig.DEBUG) {
            Header("Ontwikkeling")
            SettingRow("EPG controleren", epgCheck, onClick = vm::checkEpg, leading = Icons.Filled.BugReport)
            SettingRow("Screensaver bekijken", onClick = { dreamPreview = true }, leading = Icons.Filled.Wallpaper)
        }
    }

    if (dreamPreview) {
        Dialog(onDismissRequest = { dreamPreview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            DreamScreen(vm::dreamPosters)
        }
    }

    gates?.let { (id, remaining) ->
        val hashes = remaining.first()
        PinDialog(
            title = if (remaining.size > 1 || hashes.size > 1) "Pincode van een volwassene" else "Pincode van $profileName",
            onPin = { pin ->
                if (hashes.any { Pin.verify(pin, it) }) {
                    val rest = remaining.drop(1)
                    if (rest.isEmpty()) { gates = null; onEditProfile(id) } else gates = id to rest
                    null
                } else "Onjuiste pincode"
            },
            onDismiss = { gates = null },
        )
    }
    if (editFollows) {
        FollowDialog(followTerms, followNext, vm::follow, vm::unfollow, onDismiss = { editFollows = false })
    }
    if (pickCountries) {
        CountryDialog(countries, prefs.preferredCountries, vm::toggleCountry, onDismiss = { pickCountries = false })
    }
    if (confirmLogout) {
        ConsequenceDialog(
            title = "Uitloggen?",
            keeps = listOf("Profielen en pincodes", "Favorieten en Verder kijken", "Zoektermen en herinneringen", "Filmgegevens en genres"),
            loses = listOf("Inloggegevens van dit abonnement", "Catalogus en gids (komen terug bij het inloggen)"),
            note = "Log je daarna in bij een andere provider, dan beginnen favorieten en Verder kijken opnieuw.",
            confirm = "Uitloggen",
            onConfirm = { vm.logout(onLoggedOut) },
            onDismiss = { confirmLogout = false },
        )
    }
    wipePin?.let { hashes ->
        PinDialog(
            title = "Pincode van een volwassene",
            onPin = { pin ->
                if (hashes.any { Pin.verify(pin, it) }) { wipePin = null; confirmWipe = true; null } else "Onjuiste pincode"
            },
            onDismiss = { wipePin = null },
        )
    }
    if (confirmWipe) {
        ConsequenceDialog(
            title = "Alles wissen?",
            keeps = emptyList(),
            loses = listOf(
                "Alle profielen en pincodes",
                "Favorieten en Verder kijken",
                "Zoektermen en herinneringen",
                "Filmgegevens en genres (uren om opnieuw op te halen)",
                "Inloggegevens, catalogus en gids",
            ),
            note = "Dit kan niet ongedaan worden gemaakt.",
            confirm = "Alles wissen",
            onConfirm = { vm.wipeAll(onLoggedOut) },
            onDismiss = { confirmWipe = false },
        )
    }
}

/**
 * A section: whose settings these are, with their avatar (or the BUIS logo for the household), so
 * "Voor Bas" and "Voor iedereen" read as two different places, not as two captions.
 */
@Composable
internal fun Header(title: String, description: String? = null, icon: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            it()
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
    }
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
    /** On/off settings show a switch instead of a value. */
    toggle: Boolean? = null,
    modifier: Modifier = Modifier,
    /** An icon in front: in the player an icon says more than a word ("Geluid"), so [title] may be empty. */
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    // Icon, title and value share one line and one centre; an explanation goes underneath, under the
    // title, so it never pulls the icon down between its two lines.
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The row's own content colour (light at rest and on focus), never the theme default.
                leading?.let { androidx.tv.material3.Icon(it, contentDescription = null, tint = androidx.tv.material3.LocalContentColor.current, modifier = Modifier.padding(end = 14.dp).size(24.dp)) }
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (destructive) danger() else androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.weight(1f))
                // Values you can change are blue; plain information stays grey.
                value?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = if (onClick != null) BrandText else TextSecondary, modifier = Modifier.padding(start = 24.dp)) }
                toggle?.let { Switch(it, Modifier.padding(start = 24.dp)) }
            }
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(start = if (leading != null) 38.dp else 0.dp)) }
        }
    }
    if (onClick == null) {
        // Not on a Surface, so nothing sets a content colour: give it the light one explicitly.
        androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides TextPrimary) { content() }
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = Focus.bar(),
    ) { content() }
}

@Composable
private fun Switch(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = 44.dp, height = 24.dp).background(if (on) Brand else SwitchOff, RoundedCornerShape(50)).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(TextPrimary, CircleShape))
    }
}

private val SwitchOff = androidx.compose.ui.graphics.Color(0xFF4A4D55)

/** Red for "Uitloggen"; darker on the focus bar so it stays readable there. */
@Composable
private fun danger() = androidx.compose.ui.graphics.Color(if (onLight()) 0xFFB42318 else 0xFFFF8A80)

/**
 * What a step costs, before taking it: a check for what stays, a cross for what goes. Focus starts on Annuleren, since
 * both uses throw something away.
 */
@Composable
private fun ConsequenceDialog(
    title: String,
    keeps: List<String>,
    loses: List<String>,
    note: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            keeps.forEach { ConsequenceLine(Icons.Filled.Check, BrandText, it) }
            loses.forEach { ConsequenceLine(Icons.Filled.Close, danger(), it) }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 8.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                SecondaryButton("Annuleren", onDismiss, Modifier.focusRequester(cancel))
                PrimaryButton(confirm, onConfirm)
            }
        }
    }
    LaunchedEffect(Unit) { cancel.requestFocus() }
}

@Composable
private fun ConsequenceLine(mark: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.tv.material3.Icon(mark, contentDescription = null, tint = color, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
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
                        text = NameParser.countryName(code),
                        selected = code in selected,
                        checked = code in selected,
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
