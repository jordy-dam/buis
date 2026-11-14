package nl.jordy.iptv.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.data.settings.IdleClock
import nl.jordy.iptv.data.settings.IdleTestbeeldStore
import javax.inject.Inject

@HiltViewModel
class IdleTestbeeldViewModel @Inject constructor(store: IdleTestbeeldStore) : ViewModel() {
    val minutes = store.minutes.stateIn(viewModelScope, SharingStarted.Eagerly, IdleTestbeeldStore.DEFAULT)
}

/**
 * Shows the testbeeld once the remote has been left alone for the household's minutes, wherever
 * [allowed] (never over the player: someone may be watching without touching anything). In its own
 * window, so whatever had focus underneath still has it when the tube warms up again, and so it
 * also covers a dialog left open.
 */
@Composable
fun IdleTestbeeldHost(allowed: Boolean, vm: IdleTestbeeldViewModel = hiltViewModel()) {
    val minutes by vm.minutes.collectAsStateWithLifecycle()
    var up by remember { mutableStateOf(false) }
    // Restarts when the testbeeld goes away, so the next quiet stretch counts again.
    LaunchedEffect(allowed, minutes, up) {
        if (up || !allowed || minutes == 0) return@LaunchedEffect
        while (true) {
            val left = minutes * 60_000L - IdleClock.idleMs()
            if (left <= 0) {
                up = true
                return@LaunchedEffect
            }
            delay(left.coerceAtMost(5_000))
        }
    }
    if (up) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
        ) {
            TestbeeldOverlay(idle = true, onDone = {
                IdleClock.touch()
                up = false
            })
        }
    }
}
