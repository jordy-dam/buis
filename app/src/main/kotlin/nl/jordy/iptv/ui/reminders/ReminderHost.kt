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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceRaised
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** Looks in the guide every 20 seconds for something the profile follows that's about to start. */
@HiltViewModel
class ReminderViewModel @Inject constructor(private val follows: FollowRepository) : ViewModel() {

    /** What was dealt with this app run ("<key>:soon", "<key>:now"), so nothing nags twice. */
    private val shown = mutableSetOf<String>()
    val due = MutableStateFlow<FollowMatches.Due?>(null)

    /** For "Volgen aanpassen" straight from a reminder. */
    val terms = follows.terms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val next = follows.terms.mapLatest { terms ->
        val found = runCatching { follows.upcoming() }.getOrDefault(emptyList())
        terms.associateWith { t -> found.firstOrNull { it.term == t } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun follow(term: String) = viewModelScope.launch { follows.add(term) }
    fun unfollow(term: String) = viewModelScope.launch { follows.remove(term) }

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
    // Outlives the reminder: "Volgen aanpassen" puts the reminder away and opens the terms in its place.
    var editFollows by remember { mutableStateOf(false) }
    if (editFollows) {
        val terms by vm.terms.collectAsStateWithLifecycle()
        val next by vm.next.collectAsStateWithLifecycle()
        FollowDialog(terms, next, vm::follow, vm::unfollow, onDismiss = { editFollows = false })
        return
    }
    val d = due ?: return
    if (!active) return
    val m = d.match
    val started = d.moment == FollowMatches.Moment.STARTED
    val live = PlayerRoute(PlayerRoute.Kind.LIVE, m.streamId, m.channelName, categoryId = m.categoryId)
    val watch = remember { FocusRequester() }
    // A card in the corner over a lightly dimmed screen, like a tv notification, not a dialog in the middle.
    Dialog(onDismissRequest = { vm.dismiss(watched = false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.25f)
        Box(Modifier.fillMaxSize().padding(SafeArea), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.ignoreHeldKey().width(560.dp).background(SurfaceRaised, RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo plate like the channel rows: logos are often transparent PNGs.
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).background(SurfaceColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(m.channelName.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    if (m.logoUrl != null) AsyncImage(model = m.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                }
                Column(Modifier.padding(start = 16.dp)) {
                    val minutes = ((m.start - System.currentTimeMillis() / 1000 + 59) / 60).coerceAtLeast(1)
                    val at = clock.format(Instant.ofEpochSecond(m.start))
                    Text(if (started) "Nu begonnen · $at" else "Over $minutes min · $at", style = MaterialTheme.typography.labelLarge, color = Live)
                    Text(m.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(m.channelName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                SecondaryButton("Volgen aanpassen", {
                    vm.dismiss(watched = true)
                    editFollows = true
                })
            }
        }
        }
    }
    LaunchedEffect(m.key, d.moment) { runCatching { watch.requestFocus() } }
}
