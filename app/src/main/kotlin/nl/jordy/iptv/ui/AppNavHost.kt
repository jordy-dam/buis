package nl.jordy.iptv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import nl.jordy.iptv.ui.detail.MovieDetailScreen
import nl.jordy.iptv.ui.detail.SeriesDetailScreen
import nl.jordy.iptv.ui.login.LoginScreen
import nl.jordy.iptv.ui.player.PlayerScreen
import nl.jordy.iptv.ui.profiles.ProfileEditScreen
import nl.jordy.iptv.ui.profiles.ProfilePickerScreen
import nl.jordy.iptv.ui.shell.ShellScreen
import nl.jordy.iptv.ui.sync.SyncScreen

/**
 * Back stack: player → detail → shell (list) → exit dialog. Login, sync and the launch profile
 * picker never stay on the stack; the picker opened from the rail sits on top of the shell.
 */
@Composable
fun AppNavHost(start: Any) {
    val nav = rememberNavController()
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
                onLoggedOut = { nav.navigate(LoginRoute) { popUpTo(ShellRoute) { inclusive = true } } },
            )
        }
        composable<MovieDetailRoute> { MovieDetailScreen(onPlay = { nav.navigate(it) }) }
        composable<SeriesDetailRoute> { SeriesDetailScreen(onPlay = { nav.navigate(it) }) }
        composable<PlayerRoute> { PlayerScreen(onExit = { nav.popBackStack() }) }
    }
}
