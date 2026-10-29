package nl.jordy.iptv.ui.shell

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.jordy.iptv.data.epg.EpgGuide
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.repo.MovieEnricher
import nl.jordy.iptv.ui.components.AvatarCircle
import nl.jordy.iptv.ui.home.HomeScreen
import javax.inject.Inject
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.favorites.FavoritesScreen
import nl.jordy.iptv.ui.live.LiveScreen
import nl.jordy.iptv.ui.search.SearchScreen
import nl.jordy.iptv.ui.settings.SettingsScreen
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextSecondary
import nl.jordy.iptv.ui.vod.MoviesScreen
import nl.jordy.iptv.ui.vod.SeriesScreen

/** Tabs of the rail. Home is the start tab: the watching profile's own rows. */
enum class Tab(val label: String, val icon: String) {
    // First, like Google TV: the fastest way into 100k items is one step from the top.
    SEARCH("Zoeken", "⌕"),
    HOME("Home", "⌂"),
    LIVE("Live TV", "▶"),
    MOVIES("Films", "■"),
    SERIES("Series", "≡"),
    // U+FE0E: text-style heart; without it Android draws a red emoji that ignores the theme.
    FAVORITES("Favorieten", "♥︎"),
    // Pinned to the bottom of the rail, away from the content tabs.
    SETTINGS("Instellingen", "⚙︎"),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShellScreen(
    onSwitchProfile: () -> Unit,
    onPlay: (PlayerRoute) -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onRefreshCatalog: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    vm: ShellViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var confirmExit by remember { mutableStateOf(false) }
    val railFocus = remember { Tab.entries.associateWith { FocusRequester() } }
    val activity = LocalActivity.current
    // Saveable, so returning from the player doesn't yank focus back to the rail.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialFocusDone) {
            initialFocusDone = true
            runCatching { railFocus.getValue(tab).requestFocus() }
        }
    }

    // Someone else picked up the remote: start them on their own Home, not on the last one's tab.
    var shownProfile by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(profile?.id) {
        val id = profile?.id ?: return@LaunchedEffect
        if (shownProfile != null && shownProfile != id) {
            tab = Tab.HOME
            runCatching { railFocus.getValue(Tab.HOME).requestFocus() }
        }
        shownProfile = id
    }


    // The rail steps back to icons while you browse, and opens again when focus comes back to it.
    var railOpen by remember { mutableStateOf(true) }
    val railWidth by animateDpAsState(if (railOpen) 176.dp else 64.dp, tween(150, easing = FastOutSlowInEasing), label = "rail")

    // Walking down the rail doesn't build every screen on the way: a tab opens once focus rests on
    // it for a moment, or at once on OK or Right.
    var pendingTab by remember { mutableStateOf<Tab?>(null) }
    LaunchedEffect(pendingTab) {
        val t = pendingTab ?: return@LaunchedEffect
        delay(TAB_SETTLE_MS)
        tab = t
    }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    fun openNow(t: Tab) {
        pendingTab = null
        tab = t
    }

    // Back: any tab → Home → "Afsluiten?". Screens handle their own inner Back first.
    BackHandler {
        if (tab != Tab.HOME) {
            pendingTab = null
            tab = Tab.HOME
            // After the frame that removes the old screen: otherwise its focused item disappears
            // afterwards and the rail's focus restorer puts focus back on the old tab.
            scope.launch {
                withFrameNanos { }
                runCatching { railFocus.getValue(Tab.HOME).requestFocus() }
            }
        } else confirmExit = true
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(railWidth)
                .background(SurfaceColor)
                .padding(start = 12.dp, end = 8.dp, top = SafeArea, bottom = SafeArea)
                .onFocusChanged { railOpen = it.hasFocus }
                .focusRestorer(railFocus.getValue(tab))
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Who's watching, top left: the switch point. The BUIS logo lives on "Wie kijkt er?".
            profile?.let { ProfileItem(it.name, it.avatar, it.face, railOpen, onSwitchProfile) }
            Spacer(Modifier.height(12.dp))
            Tab.entries.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(
                    t,
                    selected = t == tab,
                    open = railOpen,
                    onFocus = { pendingTab = t },
                    onClick = { openNow(t) },
                    modifier = Modifier.focusRequester(railFocus.getValue(t)).onPreviewKeyEvent { e ->
                        // Right before the tab has opened: open it, then move into the new content.
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && tab != t) {
                            openNow(t)
                            scope.launch {
                                withFrameNanos { }
                                focusManager.moveFocus(FocusDirection.Right)
                            }
                            true
                        } else false
                    },
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(top = SafeArea, end = SafeArea, start = 24.dp)) {
            when (tab) {
                Tab.HOME -> HomeScreen(onPlay = onPlay, onOpenMovie = onOpenMovie)
                Tab.LIVE -> LiveScreen(onPlay = onPlay)
                Tab.MOVIES -> MoviesScreen(onOpenMovie = onOpenMovie)
                Tab.SERIES -> SeriesScreen(onOpenSeries = onOpenSeries)
                Tab.FAVORITES -> FavoritesScreen(onPlay = onPlay, onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries)
                Tab.SEARCH -> SearchScreen(onPlay = onPlay, onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries)
                Tab.SETTINGS -> SettingsScreen(onRefreshCatalog = onRefreshCatalog, onLoggedOut = onLoggedOut, onEditProfile = onEditProfile)
            }
        }
    }

    if (confirmExit) {
        ExitDialog(onExit = { activity?.finish() }, onDismiss = { confirmExit = false })
    }
}

@HiltViewModel
class ShellViewModel @Inject constructor(profiles: ProfileRepository, guide: EpgGuide, enricher: MovieEnricher) : ViewModel() {
    val profile = profiles.current

    init {
        // Once a day, in the background; until then channel rows use the short EPG.
        guide.refreshIfStale()
        // Film details for the genre filter: resumes where it stopped, then only new films.
        enricher.start()
    }
}

/**
 * Who's watching, at the top of the rail. Unlike the tabs, focusing it does nothing: OK opens
 * "Wie kijkt er?", so switching is Up, OK, (Left/Right), OK.
 */
@Composable
private fun ProfileItem(name: String, avatar: Int, face: Int, open: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = Focus.bar(container = androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(name, avatar, 32.dp, face = face)
            if (open) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** Focusing a rail item switches the tab, like the Google TV home screen; OK moves into the content. */
@Composable
private fun RailItem(tab: Tab, selected: Boolean, open: Boolean, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = Focus.bar(container = if (selected) Brand.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(tab.icon, color = if (selected) androidx.compose.ui.graphics.Color.Unspecified else TextSecondary, modifier = Modifier.width(24.dp))
            if (open) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Medium else null,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

private const val TAB_SETTLE_MS = 150L

@Composable
private fun ExitDialog(onExit: () -> Unit, onDismiss: () -> Unit) {
    val stay = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("BUIS afsluiten?", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Default focus on staying: a stray double Back shouldn't close the app.
                SecondaryButton("Blijven", onDismiss, Modifier.focusRequester(stay))
                PrimaryButton("Afsluiten", onExit)
            }
        }
    }
    LaunchedEffect(Unit) { stay.requestFocus() }
}
