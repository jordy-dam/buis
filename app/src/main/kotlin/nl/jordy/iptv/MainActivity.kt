package nl.jordy.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.epg.EpgGuide
import nl.jordy.iptv.data.repo.CalendarRepository
import nl.jordy.iptv.ui.AppNavHost
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.ui.LoginRoute
import nl.jordy.iptv.ui.ProfilesRoute
import nl.jordy.iptv.ui.ShellRoute
import nl.jordy.iptv.ui.SyncRoute
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.GarageCar
import nl.jordy.iptv.ui.theme.IptvTheme
import nl.jordy.iptv.ui.theme.Look
import nl.jordy.iptv.ui.theme.TextPrimary
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: StartViewModel by viewModels()
    @Inject lateinit var guide: EpgGuide
    @Inject lateinit var calendars: CalendarRepository
    @Inject lateinit var catalog: nl.jordy.iptv.data.sync.CatalogRefresh

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val look by vm.look.collectAsStateWithLifecycle()
            val car by vm.car.collectAsStateWithLifecycle()
            IptvTheme(look, car) {
                // tv Surface provides the content colour; without it Text falls back to black.
                Surface(Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = Background, contentColor = TextPrimary)) {
                    val start by vm.start.collectAsStateWithLifecycle()
                    start?.let { AppNavHost(it) }
                }
            }
        }
    }

    // Every key counts as someone being there, for the idle testbeeld.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        nl.jordy.iptv.data.settings.IdleClock.touch()
        return super.dispatchKeyEvent(event)
    }

    // Every return to the app is a chance to pick up a newer guide and calendars, in the background.
    // The catalog too, as a safety net: normally the screensaver already did it.
    override fun onStart() {
        super.onStart()
        nl.jordy.iptv.data.settings.IdleClock.touch()
        guide.refreshIfStale()
        calendars.refreshIfStale()
        catalog.refreshIfStale()
    }
}

/**
 * Picks the first screen: login when there are no credentials, then "Wie kijkt er?" when there is
 * more than one profile, then sync only when there is no catalog yet. A day-old catalog is
 * refreshed in the background ([nl.jordy.iptv.data.sync.CatalogRefresh]).
 */
@HiltViewModel
class StartViewModel @Inject constructor(
    credentials: CredentialStore,
    db: AppDatabase,
    profiles: ProfileRepository,
) : ViewModel() {
    private val _start = MutableStateFlow<Any?>(null)
    val start = _start.asStateFlow()
    /** The watching profile's colours; switching profile switches them. */
    val look = profiles.current.map { Look.of(it?.look ?: 0) }.stateIn(viewModelScope, SharingStarted.Eagerly, Look.STANDARD)
    /** The car behind Garage's menu screens. */
    val car = profiles.current.map { GarageCar.of(it?.car ?: 0) }.stateIn(viewModelScope, SharingStarted.Eagerly, GarageCar.PORSCHE)

    init {
        viewModelScope.launch {
            val creds = runCatching { credentials.current() }.getOrNull()
            profiles.ensureOne()
            val noCatalog = db.syncInfo().get() == null
            _start.value = when {
                creds == null -> LoginRoute
                profiles.all().size > 1 -> ProfilesRoute(atLaunch = true, syncAfter = noCatalog)
                noCatalog -> SyncRoute
                else -> ShellRoute
            }
        }
    }

}
