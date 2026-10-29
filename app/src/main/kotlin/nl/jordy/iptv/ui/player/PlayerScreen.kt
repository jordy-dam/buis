package nl.jordy.iptv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.jordy.iptv.player.PlayerViewModel
import nl.jordy.iptv.player.PlayerUiState
import nl.jordy.iptv.player.REPLAY_MS
import nl.jordy.iptv.ui.settings.SettingRow
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.SubtitleView
import nl.jordy.iptv.ui.components.BreakIcons
import nl.jordy.iptv.ui.components.CornerCard
import nl.jordy.iptv.ui.components.PrimaryButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ColorFilter
import nl.jordy.iptv.ui.components.ProgressBar
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import nl.jordy.iptv.player.NextUpCard
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.TextOnLight
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onExit: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Bumping this shows the overlay; it hides itself a few seconds after the last key.
    var overlayTick by remember { mutableIntStateOf(1) }
    var overlayVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // Audio, subtitles and the clock: Down in a film or episode, Right in live (Down zaps there).
    var optionsOpen by remember { mutableStateOf(false) }
    // Instant replay: this hold of Left already rewound, so its release isn't "vorige zender".
    var leftHeld by remember { mutableStateOf(false) }

    LaunchedEffect(overlayTick) {
        overlayVisible = true
        delay(if (vm.isLive) 4_000 else 3_000)
        overlayVisible = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Keep the screen on while something actually plays: without it the Google TV screensaver
    // takes over after its timeout (10 min on the TCL) in the middle of a film. Paused, it may come.
    val view = LocalView.current
    DisposableEffect(vm.player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { view.keepScreenOn = isPlaying }
        }
        vm.player.addListener(listener)
        view.keepScreenOn = vm.player.isPlaying
        onDispose {
            vm.player.removeListener(listener)
            view.keepScreenOn = false
        }
    }
    // Films, episodes and live programmes watched from the start all show a position.
    LaunchedEffect(Unit) {
        while (true) {
            position = vm.player.currentPosition
            duration = vm.player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    // Home button / screen off: pause and remember the position. Live resumes on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { vm.cancelBreak(); vm.saveNow(); vm.player.pause() }
                Lifecycle.Event.ON_START -> if (vm.isLive) vm.player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BackHandler { vm.saveNow(); onExit() }
    // Watching from the start: Back returns to the channel live, a second Back leaves.
    BackHandler(enabled = state.catchup != null) { vm.backToLive(); overlayTick++ }
    // Declared later, so it wins while the card shows: Back stops the countdown, not the episode.
    BackHandler(enabled = state.nextUp != null) { vm.dismissNextUp() }
    // Back on the break card: no break this time, play on.
    BackHandler(enabled = state.breakSecondsLeft != null) { vm.endBreak() }
    BackHandler(enabled = state.skipIntroTo != null && !optionsOpen) { vm.dismissIntro() }
    BackHandler(enabled = optionsOpen) { optionsOpen = false; focus.requestFocus() }
    LaunchedEffect(state.finished) { if (state.finished) onExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                val code = e.key.nativeKeyCode
                // Live: Left is "vorige zender" when released; held, it rewinds (instant replay).
                val liveLeft = vm.isLive && state.catchup == null && code == KeyEvent.KEYCODE_DPAD_LEFT && !optionsOpen &&
                    state.sleepSecondsLeft == null && state.breakSecondsLeft == null
                if (liveLeft) {
                    vm.userActive()
                    if (e.type == KeyEventType.KeyDown) {
                        if (e.nativeKeyEvent.repeatCount > 0 && !leftHeld) {
                            leftHeld = true
                            vm.replay()
                            overlayTick++
                        }
                    } else if (e.type == KeyEventType.KeyUp) {
                        if (!leftHeld) vm.previousChannel()
                        leftHeld = false
                        overlayTick++
                    }
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                // The sleep timer's "still watching?": the key that answers it does nothing else.
                val wasAsking = state.sleepSecondsLeft != null
                vm.userActive()
                if (wasAsking) return@onKeyEvent true
                // The options panel has focus: its rows handle the keys.
                if (optionsOpen) return@onKeyEvent false
                // During the break only OK (and play) end it; someone walking past the remote doesn't.
                if (state.breakSecondsLeft != null) {
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                    ) vm.endBreak()
                    return@onKeyEvent code != KeyEvent.KEYCODE_BACK
                }
                if (state.skipIntroTo != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                    vm.skipIntro()
                    return@onKeyEvent true
                }
                if (state.nextUp != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                    vm.playNextNow()
                    return@onKeyEvent true
                }
                // Live from the start behaves like a film: pause and seek.
                val live = vm.isLive && state.catchup == null
                val handled = if (live) when (code) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.previousChannel(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { optionsOpen = true; true }
                    // First OK shows the info with "OK · vanaf begin"; OK while it shows starts it.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (state.replaying) vm.goLive() else if (overlayVisible && state.canRestart) vm.watchFromStart()
                        true
                    }
                    KeyEvent.KEYCODE_INFO -> true
                    else -> false
                } else when (code) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.togglePause(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(30_000); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { optionsOpen = true; true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_INFO -> true
                    else -> false
                }
                if (handled) overlayTick++
                handled
            }
            .focusable(),
    ) {
        val presentation = rememberPresentationState(vm.player)
        PlayerSurface(
            player = vm.player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.fillMaxSize().resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
        )
        Subtitles(vm.player, raised = overlayVisible && state.error == null && !optionsOpen, Modifier.fillMaxSize())

        if (state.buffering && state.error == null) {
            Text("Laden…", style = MaterialTheme.typography.titleLarge, color = TextSecondary, modifier = Modifier.align(Alignment.Center))
        }

        state.error?.let { error ->
            Column(Modifier.align(Alignment.Center).width(640.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(state.title, style = MaterialTheme.typography.headlineMedium)
                Text(error, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                val retry = remember { FocusRequester() }
                PrimaryButton("Opnieuw proberen", vm::retry, Modifier.focusRequester(retry))
                LaunchedEffect(error) { retry.requestFocus() }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible && state.error == null && !optionsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = SafeArea, end = SafeArea, bottom = SafeArea, top = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            state.catchup != null -> CatchupInfo(state, state.catchup!!, position, duration, vm.player.isPlaying)
                            vm.isLive -> LiveInfo(state)
                            else -> VodInfo(state.title, position, duration, vm.player.isPlaying)
                        }
                    }
                    // Subtle, level with the title: a glance at the time without a second bar.
                    if (state.showClock) ClockText(Modifier.align(Alignment.TopEnd))
                }
            }
        }

        // One card at a time, in the corner every card uses. The sleep timer wins: it stops playback.
        val corner = Modifier.align(Alignment.BottomEnd).padding(SafeArea)
        when {
            optionsOpen -> OptionsPanel(state, vm, corner)
            state.sleepSecondsLeft != null -> SleepCard(state.sleepSecondsLeft!!, corner)
            state.breakSecondsLeft != null -> BreakCard(state.breakSecondsLeft!!, corner)
            state.nextUp != null -> NextEpisodeCard(state.nextUp!!, corner)
            state.skipIntroTo != null -> IntroCard(state.skipIntroTo!!, corner)
        }
    }
}

@Composable
private fun LiveInfo(state: nl.jordy.iptv.player.PlayerUiState) {
    Row(verticalAlignment = Alignment.Bottom) {
        state.channelNumber?.let { Text("$it  ", style = MaterialTheme.typography.headlineMedium, color = Live) }
        Text(state.title, style = MaterialTheme.typography.headlineMedium)
    }
    state.now?.let { now ->
        Text("${clock.format(Instant.ofEpochSecond(now.start))}–${clock.format(Instant.ofEpochSecond(now.stop))}  ${now.title}", style = MaterialTheme.typography.titleLarge)
        val total = (now.stop - now.start).coerceAtLeast(1)
        val elapsed = System.currentTimeMillis() / 1000 - now.start
        ProgressBar(elapsed.toFloat() / total, Modifier.width(480.dp))
    }
    state.next?.let { Text("Straks ${clock.format(Instant.ofEpochSecond(it.start))}  ${it.title}", style = MaterialTheme.typography.bodyLarge, color = TextSecondary) }
    Text(
        when {
            state.replaying -> "⟲ ${REPLAY_MS / 1000} s terug gespoeld   OK naar live   ◀ vasthouden: verder terug"
            else -> "▲▼ andere zender   ◀ " + (state.previous?.let { "terug naar $it, " } ?: "") + "vasthouden: terugspoelen" +
                (if (state.canRestart) "   OK vanaf begin" else "") + "   ▶ opties"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
    )
    state.replayHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Live) }
}

/** A live programme from its start: the channel, the programme, film-style position and keys. */
@Composable
private fun CatchupInfo(state: nl.jordy.iptv.player.PlayerUiState, programme: EpgEntry, position: Long, duration: Long, playing: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        state.channelNumber?.let { Text("$it  ", style = MaterialTheme.typography.headlineMedium, color = Live) }
        Text(state.title, style = MaterialTheme.typography.headlineMedium)
    }
    Text(
        "Vanaf begin · ${clock.format(Instant.ofEpochSecond(programme.start))}  ${programme.title}",
        style = MaterialTheme.typography.titleLarge,
    )
    if (duration > 0) ProgressBar(position.toFloat() / duration)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${if (playing) "▶" else "❚❚"}  ${formatTime(position)}", style = MaterialTheme.typography.titleMedium)
        Text("◀ 10s   OK pauze   30s ▶   ▼ opties   Terug: live", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/** Over the end credits: what's next and the two keys that matter. */
@Composable
private fun NextEpisodeCard(card: NextUpCard, modifier: Modifier) {
    CornerCard(
        label = card.secondsLeft?.let { "Volgende aflevering over $it s" } ?: "Kijk je nog?",
        title = card.title,
        modifier = modifier,
        visual = { Text(card.code, style = MaterialTheme.typography.titleMedium, color = TextSecondary) },
    ) {
        KeyHint("OK", if (card.secondsLeft == null) "Volgende aflevering" else "Nu kijken", primary = true)
        KeyHint("Terug", "Stoppen", primary = false)
    }
}

/** Pauzemoment, over the paused picture: toilet, drink, snack, and when it goes on. */
@Composable
private fun BreakCard(secondsLeft: Int, modifier: Modifier) {
    CornerCard(
        label = "Pauze · verder over ${formatTime(secondsLeft * 1000L)}",
        title = "Even plassen, drinken of iets pakken",
        modifier = modifier,
        visual = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BreakIcons.all.forEach {
                    Image(it, contentDescription = null, colorFilter = ColorFilter.tint(TextSecondary), modifier = Modifier.size(28.dp))
                }
            }
        },
    ) {
        KeyHint("OK", "Verder", primary = true)
        KeyHint("Terug", "Overslaan", primary = false)
    }
}

/**
 * Audio, subtitles and the clock, in the corner with the same surface as the cards. Rows like in
 * Instellingen: OK steps to the next choice. Back closes it.
 */
@Composable
private fun OptionsPanel(state: PlayerUiState, vm: PlayerViewModel, modifier: Modifier) {
    val first = remember { FocusRequester() }
    Column(
        modifier.width(560.dp).background(SurfaceRaised, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Opties", style = MaterialTheme.typography.labelLarge, color = Live)
        val audioClickable = state.audio.size > 1
        val subsClickable = state.subtitles.isNotEmpty()
        SettingRow(
            "Geluid",
            value = state.audioSelected?.let { state.audio.getOrNull(it)?.label } ?: "Standaard",
            onClick = if (audioClickable) vm::nextAudio else null,
            modifier = if (audioClickable) Modifier.focusRequester(first) else Modifier,
        )
        SettingRow(
            "Ondertitels",
            value = if (!subsClickable) "Geen" else state.subtitleSelected?.let { state.subtitles.getOrNull(it)?.label } ?: "Uit",
            onClick = if (subsClickable) vm::nextSubtitle else null,
            modifier = if (subsClickable && !audioClickable) Modifier.focusRequester(first) else Modifier,
        )
        SettingRow(
            "Klok",
            toggle = state.showClock,
            onClick = vm::toggleClock,
            modifier = if (!audioClickable && !subsClickable) Modifier.focusRequester(first) else Modifier,
        )
        Text("Geluid en ondertitels gelden voor deze titel. Vaste talen: Instellingen → Voor jou.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun ClockText(modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(10_000)
        }
    }
    Text(clock.format(Instant.ofEpochMilli(now)), style = MaterialTheme.typography.titleMedium, color = TextSecondary, modifier = modifier)
}

/**
 * Compose has no subtitle renderer yet: Media3's own view, fed with the player's cues. [raised]
 * lifts the text above the info bar while it shows, so the two never overlap. The view itself gets
 * shorter: many subtitle formats place cues at a fixed fraction of the height, which ignores padding.
 */
@OptIn(UnstableApi::class)
@Composable
private fun Subtitles(player: Player, raised: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val view = remember { SubtitleView(context).apply { setUserDefaultStyle(); setUserDefaultTextSize() } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) = view.setCues(cueGroup.cues)
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    AndroidView({ view }, modifier.padding(bottom = if (raised) 170.dp else 0.dp))
}

/** Early in an episode of a series whose intro BUIS learned: one OK jumps past it. */
@Composable
private fun IntroCard(to: Long, modifier: Modifier) {
    CornerCard(
        label = "Intro en terugblik · tot ${formatTime(to)}",
        title = "Intro overslaan",
        modifier = modifier,
    ) {
        KeyHint("OK", "Overslaan", primary = true)
        KeyHint("Terug", "Kijken", primary = false)
    }
}

@Composable
private fun SleepCard(secondsLeft: Int, modifier: Modifier) {
    CornerCard(
        label = "Slaaptimer · stopt over $secondsLeft s",
        title = "Kijk je nog?",
        subtitle = "Druk op een knop om door te kijken.",
        modifier = modifier,
    ) {
        KeyHint("Elke knop", "Doorkijken", primary = true)
    }
}

/** A key and what it does; the primary one looks like the focus bar, so it reads as "this one". */
@Composable
private fun KeyHint(key: String, action: String, primary: Boolean) {
    val shape = RoundedCornerShape(50)
    Text(
        "$key · $action",
        style = MaterialTheme.typography.labelLarge,
        color = if (primary) TextOnLight else TextPrimary,
        modifier = (if (primary) Modifier.background(FocusBar, shape) else Modifier.border(1.dp, TextSecondary, shape))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun VodInfo(title: String, position: Long, duration: Long, playing: Boolean) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (duration > 0) ProgressBar(position.toFloat() / duration)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${if (playing) "▶" else "❚❚"}  ${formatTime(position)}", style = MaterialTheme.typography.titleMedium)
        Text("◀ 10s   OK pauze   30s ▶   ▼ opties", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(if (duration > 0) formatTime(duration) else "", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
    }
}

internal fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
