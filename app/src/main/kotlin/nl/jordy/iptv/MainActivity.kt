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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.ui.AppNavHost
import nl.jordy.iptv.ui.LoginRoute
import nl.jordy.iptv.ui.ShellRoute
import nl.jordy.iptv.ui.SyncRoute
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.IptvTheme
import nl.jordy.iptv.ui.theme.TextPrimary
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IptvTheme {
                // tv Surface provides the content colour; without it Text falls back to black.
                Surface(Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = Background, contentColor = TextPrimary)) {
                    val start by vm.start.collectAsStateWithLifecycle()
                    start?.let { AppNavHost(it) }
                }
            }
        }
    }
}

/** Picks the first screen: login when there are no credentials, sync when the catalog is missing or a day old. */
@HiltViewModel
class StartViewModel @Inject constructor(
    credentials: CredentialStore,
    db: AppDatabase,
) : ViewModel() {
    private val _start = MutableStateFlow<Any?>(null)
    val start = _start.asStateFlow()

    init {
        viewModelScope.launch {
            val creds = runCatching { credentials.current() }.getOrNull()
            val lastSync = db.syncInfo().get()?.lastSyncAt ?: 0
            _start.value = when {
                creds == null -> LoginRoute
                System.currentTimeMillis() - lastSync > RESYNC_AFTER_MS -> SyncRoute
                else -> ShellRoute
            }
        }
    }

    private companion object {
        const val RESYNC_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
