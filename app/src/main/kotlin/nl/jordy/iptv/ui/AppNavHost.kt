package nl.jordy.iptv.ui

import androidx.compose.runtime.getValue
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import nl.jordy.iptv.ui.reminders.BedtimeHost
import nl.jordy.iptv.ui.reminders.ReminderHost
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import nl.jordy.iptv.ui.reminders.BedtimeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import nl.jordy.iptv.ui.reminders.ReminderViewModel

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import nl.jordy.iptv.ui.detail.MovieDetailScreen
import nl.jordy.iptv.ui.guide.TvGuideScreen
import nl.jordy.iptv.ui.history.HistoryScreen
import nl.jordy.iptv.ui.settings.ChannelHealthScreen
import nl.jordy.iptv.ui.detail.SeriesDetailScreen
import nl.jordy.iptv.ui.login.LoginScreen
import nl.jordy.iptv.ui.player.PlayerScreen
import nl.jordy.iptv.ui.profiles.ProfileEditScreen
import nl.jordy.iptv.ui.profiles.ProfilePickerScreen
import nl.jordy.iptv.ui.shell.ShellScreen
import nl.jordy.iptv.ui.sync.SyncScreen
import nl.jordy.iptv.ui.taste.TasteProfileScreen
import nl.jordy.iptv.ui.taste.TasteScreen

/**
 * Back stack: player → detail → shell (list) → exit dialog. Login, sync and the launch profile
 * picker never stay on the stack; the picker opened from the rail sits on top of the shell.
 */
@Composable
fun AppNavHost(start: Any) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    // Reminders only where someone is watching: not over login, the catalog sync or "Wie kijkt er?".
    val dest = entry?.destination
    val watching = dest != null && (dest.hasRoute<ShellRoute>() || dest.hasRoute<PlayerRoute>() || dest.hasRoute<MovieDetailRoute>() || dest.hasRoute<SeriesDetailRoute>() || dest.hasRoute<HistoryRoute>() || dest.hasRoute<TvGuideRoute>() || dest.hasRoute<TasteRoute>() || dest.hasRoute<SearchRoute>())
    // One card at a time: whichever is up stays until it's answered, the other waits and then opens
    // with focus of its own. Reminders go first when both are due at once.
    val reminders: ReminderViewModel = hiltViewModel()
    val bedtime: BedtimeViewModel = hiltViewModel()
    val reminderUp = reminders.due.collectAsStateWithLifecycle().value != null
    val bedtimeDue = bedtime.due.collectAsStateWithLifecycle().value != null
    var bedtimeOnScreen by remember { mutableStateOf(false) }
    if (!bedtimeDue) bedtimeOnScreen = false else if (!reminderUp) bedtimeOnScreen = true
    ReminderHost(watching && !bedtimeOnScreen, vm = reminders, onWatch = { route ->
        // From another channel or a film: the reminder's channel replaces it, Back still leads home.
        if (nav.currentDestination?.hasRoute<PlayerRoute>() == true) nav.popBackStack()
        nav.navigate(route)
    })
    BedtimeHost(watching && bedtimeOnScreen, vm = bedtime)
    val watched by hiltViewModel<WatchedViewModel>().films.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(nl.jordy.iptv.ui.components.LocalWatchedFilms provides watched) {
    NavHost(navController = nav, startDestination = start) {
        composable<LoginRoute> {
            LoginScreen(onLoggedIn = { nav.navigate(SyncRoute) { popUpTo(LoginRoute) { inclusive = true } } })
        }
        composable<SyncRoute> {
            SyncScreen(
                onDone = { nav.navigate(ShellRoute) { popUpTo(SyncRoute) { inclusive = true } } },
                onLogout = { nav.navigate(LoginRoute) { popUpTo(SyncRoute) { inclusive = true } } },
            )
        }
        composable<ProfilesRoute> { entry ->
            val route = entry.toRoute<ProfilesRoute>()
            ProfilePickerScreen(
                atLaunch = route.atLaunch,
                onPicked = {
                    if (route.atLaunch) {
                        nav.navigate(if (route.syncAfter) SyncRoute else ShellRoute) { popUpTo<ProfilesRoute> { inclusive = true } }
                    } else nav.popBackStack()
                },
                onEdit = { nav.navigate(ProfileEditRoute(it)) },
            )
        }
        composable<ProfileEditRoute> { ProfileEditScreen(onDone = { nav.popBackStack() }) }
        composable<ShellRoute> {
            ShellScreen(
                onSwitchProfile = { nav.navigate(ProfilesRoute(atLaunch = false)) },
                onPlay = { nav.navigate(it) },
                onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                onOpenSeries = { nav.navigate(SeriesDetailRoute(it)) },
                onRefreshCatalog = { nav.navigate(SyncRoute) { popUpTo(ShellRoute) { inclusive = true } } },
                onEditProfile = { nav.navigate(ProfileEditRoute(it)) },
                onOpenHistory = { nav.navigate(HistoryRoute) },
                onOpenGuide = { nav.navigate(TvGuideRoute(it)) },
                onOpenChannelHealth = { nav.navigate(ChannelHealthRoute) },
                onOpenTaste = { nav.navigate(TasteRoute(it)) },
                onOpenTasteProfile = { nav.navigate(TasteProfileRoute) },
                onLoggedOut = { nav.navigate(LoginRoute) { popUpTo(ShellRoute) { inclusive = true } } },
            )
        }
        composable<MovieDetailRoute> { MovieDetailScreen(onPlay = { nav.navigate(it) }) }
        composable<SeriesDetailRoute> { SeriesDetailScreen(onPlay = { nav.navigate(it) }) }
        composable<PlayerRoute> { PlayerScreen(onExit = { nav.popBackStack() }) }
        composable<HistoryRoute> { HistoryScreen(onPlay = { nav.navigate(it) }) }
        composable<ChannelHealthRoute> { ChannelHealthScreen() }
        composable<TvGuideRoute> { TvGuideScreen(onPlay = { nav.navigate(it) }) }
        composable<TasteRoute> { TasteScreen(onDone = { nav.popBackStack() }) }
        composable<TasteProfileRoute> { TasteProfileScreen(onLearn = { nav.navigate(TasteRoute()) }, onSearch = { nav.navigate(SearchRoute(it)) }) }
        composable<SearchRoute> { entry ->
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.fillMaxSize().background(nl.jordy.iptv.ui.theme.Background).padding(nl.jordy.iptv.ui.theme.SafeArea),
            ) {
                nl.jordy.iptv.ui.search.SearchScreen(
                    onPlay = { nav.navigate(it) },
                    onOpenMovie = { nav.navigate(MovieDetailRoute(it)) },
                    onOpenSeries = { nav.navigate(SeriesDetailRoute(it)) },
                    initialQuery = entry.toRoute<SearchRoute>().query,
                )
            }
        }
    }
    }
}

/** Watched films for the posters' check, for the whole app. */
@dagger.hilt.android.lifecycle.HiltViewModel
class WatchedViewModel @javax.inject.Inject constructor(library: nl.jordy.iptv.data.repo.LibraryRepository) : androidx.lifecycle.ViewModel() {
    val films = library.watchedFilms
        // Each new set (start, profile switch, a film just watched) goes to the log, see logWatched.
        .onEach { runCatching { library.logWatched() } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, nl.jordy.iptv.data.repo.WatchedFilms())
}
