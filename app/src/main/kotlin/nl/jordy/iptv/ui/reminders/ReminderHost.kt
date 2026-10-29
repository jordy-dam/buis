package nl.jordy.iptv.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.repo.FollowMatches
import nl.jordy.iptv.data.repo.FollowRepository
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

/** Looks in the guide every 20 seconds for something the profile follows that's about to start. */
@HiltViewModel
class ReminderViewModel @Inject constructor(private val follows: FollowRepository) : ViewModel() {

    /** What was dealt with this app run ("<key>:soon", "<key>:now"), so nothing nags twice. */
    private val shown = mutableSetOf<String>()
    val due = MutableStateFlow<FollowMatches.Due?>(null)

    init {
        viewModelScope.launch {
            while (isActive) {
                if (due.value == null) {
                    val now = System.currentTimeMillis() / 1000
                    val matches = runCatching { follows.upcoming(now, hours = 1) }.getOrDefault(emptyList())
                    due.value = FollowMatches.due(matches, now, shown)
                }
                delay(20_000)
            }
        }
    }

    /** [watched]: Kijken or Vanaf begin, so no second reminder at the start. */
    fun dismiss(watched: Boolean) {
        val d = due.value ?: return
        shown += "${d.match.key}:soon"
        if (watched || d.moment == FollowMatches.Moment.STARTED) shown += "${d.match.key}:now"
        due.value = null
    }
}

/**
 * The reminder, over whatever is on screen (also a film that's playing). It takes focus on purpose:
 * one OK tunes in, Back or Later puts it away. Only while [active], so never over login or the picker.
 */
@Composable
fun ReminderHost(active: Boolean, onWatch: (PlayerRoute) -> Unit, vm: ReminderViewModel = hiltViewModel()) {
    val due by vm.due.collectAsStateWithLifecycle()
    val d = due ?: return
    if (!active) return
    val m = d.match
    val started = d.moment == FollowMatches.Moment.STARTED
    val live = PlayerRoute(PlayerRoute.Kind.LIVE, m.streamId, m.channelName, categoryId = m.categoryId)
    val watch = remember { FocusRequester() }
    Dialog(onDismissRequest = { vm.dismiss(watched = false) }) {
        Column(
            Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(28.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val minutes = ((m.start - System.currentTimeMillis() / 1000 + 59) / 60).coerceAtLeast(1)
            Text(if (started) "Nu begonnen" else "Over $minutes min", style = MaterialTheme.typography.labelLarge, color = Live)
            Text(m.title, style = MaterialTheme.typography.headlineMedium)
            Text(m.channelName, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 10.dp)) {
                PrimaryButton("Kijken", {
                    vm.dismiss(watched = true)
                    onWatch(live)
                }, Modifier.focusRequester(watch))
                if (started && m.hasArchive) {
                    SecondaryButton("Vanaf begin", {
                        vm.dismiss(watched = true)
                        onWatch(live.copy(fromStart = true))
                    })
                }
                SecondaryButton("Later", { vm.dismiss(watched = false) })
            }
        }
    }
    LaunchedEffect(m.key, d.moment) { runCatching { watch.requestFocus() } }
}
