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
import nl.jordy.iptv.ui.settings.SettingsScreen
import nl.jordy.iptv.ui.theme.Accent
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextSecondary
import nl.jordy.iptv.ui.vod.MoviesScreen
import nl.jordy.iptv.ui.vod.SeriesScreen

/** Phase 1 tabs. Home and Zoeken join in phase 2 (they need profiles and search). */
enum class Tab(val label: String, val icon: String) {
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
    onPlay: (PlayerRoute) -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onRefreshCatalog: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.LIVE) }
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

    // Back: any tab → Live TV → "Afsluiten?". Screens handle their own inner Back first.
    BackHandler {
        if (tab != Tab.LIVE) {
            tab = Tab.LIVE
            railFocus.getValue(Tab.LIVE).requestFocus()
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("IPTV", style = MaterialTheme.typography.headlineMedium, color = Accent, modifier = Modifier.padding(start = 12.dp, bottom = 24.dp))
            Tab.entries.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(t, selected = t == tab, onFocus = { tab = t }, modifier = Modifier.focusRequester(railFocus.getValue(t)))
            }
        }
        Box(Modifier.fillMaxSize().padding(top = SafeArea, end = SafeArea, start = 24.dp)) {
            when (tab) {
                Tab.LIVE -> LiveScreen(onPlay = onPlay)
                Tab.MOVIES -> MoviesScreen(onOpenMovie = onOpenMovie)
                Tab.SERIES -> SeriesScreen(onOpenSeries = onOpenSeries)
                Tab.FAVORITES -> FavoritesScreen(onPlay = onPlay, onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries)
                Tab.SETTINGS -> SettingsScreen(onRefreshCatalog = onRefreshCatalog, onLoggedOut = onLoggedOut)
            }
        }
    }

    if (confirmExit) {
        ExitDialog(onExit = { activity?.finish() }, onDismiss = { confirmExit = false })
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
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
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
            Text("IPTV afsluiten?", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Default focus on staying: a stray double Back shouldn't close the app.
                SecondaryButton("Blijven", onDismiss, Modifier.focusRequester(stay))
                PrimaryButton("Afsluiten", onExit)
            }
        }
    }
    LaunchedEffect(Unit) { stay.requestFocus() }
}
