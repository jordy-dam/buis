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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.jordy.iptv.data.profile.ProfileRepository
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
import nl.jordy.iptv.ui.theme.Accent
import nl.jordy.iptv.ui.theme.BuisWordmark
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextSecondary
import nl.jordy.iptv.ui.vod.MoviesScreen
import nl.jordy.iptv.ui.vod.SeriesScreen

/** Tabs of the rail. Home is the start tab: the watching profile's own rows. */
enum class Tab(val label: String, val icon: String) {
    HOME("Home", "⌂"),
    LIVE("Live TV", "▶"),
    MOVIES("Films", "■"),
    SERIES("Series", "≡"),
    // U+FE0E: text-style heart; without it Android draws a red emoji that ignores the theme.
    FAVORITES("Favorieten", "♥︎"),
    SEARCH("Zoeken", "⌕"),
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

    // Back: any tab → Home → "Afsluiten?". Screens handle their own inner Back first.
    BackHandler {
        if (tab != Tab.HOME) {
            tab = Tab.HOME
            railFocus.getValue(Tab.HOME).requestFocus()
        } else confirmExit = true
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(200.dp)
                .background(SurfaceColor)
                .padding(start = 24.dp, end = 12.dp, top = SafeArea, bottom = SafeArea)
                .focusRestorer(railFocus.getValue(tab))
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BuisWordmark(Modifier.padding(start = 12.dp, bottom = 8.dp))
            profile?.let { ProfileItem(it.name, it.avatar, onSwitchProfile) }
            Tab.entries.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(t, selected = t == tab, onFocus = { tab = t }, modifier = Modifier.focusRequester(railFocus.getValue(t)))
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
                Tab.SETTINGS -> SettingsScreen(onRefreshCatalog = onRefreshCatalog, onLoggedOut = onLoggedOut)
            }
        }
    }

    if (confirmExit) {
        ExitDialog(onExit = { activity?.finish() }, onDismiss = { confirmExit = false })
    }
}

@HiltViewModel
class ShellViewModel @Inject constructor(profiles: ProfileRepository) : ViewModel() {
    val profile = profiles.current
}

/**
 * Who's watching, at the top of the rail. Unlike the tabs, focusing it does nothing: OK opens
 * "Wie kijkt er?", so switching is Up, OK, (Left/Right), OK.
 */
@Composable
private fun ProfileItem(name: String, avatar: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Focus.border),
        colors = ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = SurfaceRaised),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(name, avatar, 32.dp)
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

/** Focusing a rail item switches the tab, like the Google TV home screen; OK moves into the content. */
@Composable
private fun RailItem(tab: Tab, selected: Boolean, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onFocus,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Focus.border),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = SurfaceRaised,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(tab.icon, color = if (selected) Accent else TextSecondary, modifier = Modifier.width(28.dp))
            Text(tab.label, style = MaterialTheme.typography.titleMedium, color = if (selected) Accent else MaterialTheme.colorScheme.onSurface)
        }
    }
}

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
