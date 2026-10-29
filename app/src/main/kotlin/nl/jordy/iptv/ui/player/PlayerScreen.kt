package nl.jordy.iptv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.player.NextUpCard
import nl.jordy.iptv.player.PlayerUiState
import nl.jordy.iptv.player.PlayerViewModel
import nl.jordy.iptv.player.TrackOption
import nl.jordy.iptv.ui.components.BreakIcons
import nl.jordy.iptv.ui.components.CornerCard
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.IconButton
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.components.ProgressBar
import nl.jordy.iptv.ui.theme.BrandText
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.PopupSurface
import nl.jordy.iptv.ui.theme.FocusFill
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.TextOnLight
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** How long the bar stays after the last key while something plays. Paused, it stays. */
private const val BAR_MS = 5_000L

/** How long after a pop-up went an OK still counts as meant for it (and is dropped). */
private const val CARD_GRACE_MS = 700L

/** The track list that is open above its button in the bar. */
private enum class Choice { AUDIO, SUBTITLES }

/**
 * The player. Everything you steer sits in one bar at the bottom: title, clock, progress, and a row
 * of round controls. Plain playback keeps its keys (VOD: OK pause, Left/Right seek; live: Up/Down
 * zap, Left previous channel) while the bar only shows; the controls take focus when they're asked
 * for: Down in a film, Right in live, or a pause. Audio and subtitles open their choices in place,
 * above their button. Back closes the innermost layer first: the choices, then the controls.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onExit: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Bumping this shows the bar; it hides itself a few seconds after the last key.
    var overlayTick by remember { mutableIntStateOf(1) }
    var overlayVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // Focus is in the bar's controls (not on the picture), and maybe in a track list above them.
    var controls by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    var focusTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val buttons = remember { ControlFocus() }
    // Instant replay: this hold of Left already rewound, so its release isn't "vorige zender".
    var leftHeld by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(vm.player.isPlaying) }
    var playWhenReady by remember { mutableStateOf(vm.player.playWhenReady) }
    // The bar's height, so subtitles and corner cards sit above it instead of under it.
    var barHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // Paused by the viewer, not by the Pauzemoment or the sleep timer: the bar stays then.
    val paused = !playWhenReady && state.breakSecondsLeft == null && state.sleepSecondsLeft == null && state.error == null

    fun openControls(target: FocusRequester) {
        controls = true
        overlayTick++
        focusTarget = target
    }
    fun closeControls() {
        choice = null
        controls = false
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(overlayTick, paused, choice) {
        overlayVisible = true
        if (paused || choice != null) return@LaunchedEffect
        delay(BAR_MS)
        overlayVisible = false
        if (controls) closeControls()
    }
    // The controls only exist once the bar is composed: try for a few frames.
    LaunchedEffect(focusTarget) {
        val target = focusTarget ?: return@LaunchedEffect
        // One frame first: the buttons only become focusable when `controls` has been composed, and
        // a request before that lands on the row's fallback (play) instead.
        withFrameNanos { }
        for (attempt in 0 until 10) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) break
            delay(16)
        }
        focusTarget = null
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // A pop-up asks with buttons: while it shows its main choice has focus (one OK = that choice,
    // Back = no), and when it goes, focus goes back to the picture. The sleep timer wins: it stops playback.
    val card = when {
        state.error != null -> null
        state.sleepSecondsLeft != null -> Card.SLEEP
        state.breakSecondsLeft != null -> Card.BREAK
        state.nextUp != null -> Card.NEXT
        state.skipIntroTo != null -> Card.INTRO
        else -> null
    }
    val cardFocus = remember { FocusRequester() }
    var hadCard by remember { mutableStateOf(false) }
    // When a pop-up goes by itself (the countdown ran out) just as OK is pressed, that OK was meant
    // for the pop-up: it must not also pause what comes next.
    var cardGoneAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(card) {
        if (card != null) {
            choice = null
            controls = false
            hadCard = true
            focusTarget = cardFocus
        } else if (hadCard) {
            hadCard = false
            cardGoneAt = android.os.SystemClock.uptimeMillis()
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(state.error != null) { if (state.error != null && controls) closeControls() }

    // Keep the screen on while something actually plays: without it the Google TV screensaver
    // takes over after its timeout (10 min on the TCL) in the middle of a film. Paused, it may come.
    val view = LocalView.current
    DisposableEffect(vm.player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                view.keepScreenOn = isPlaying
                playing = isPlaying
            }
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { playWhenReady = ready }
        }
        vm.player.addListener(listener)
        view.keepScreenOn = vm.player.isPlaying
        onDispose {
            vm.player.removeListener(listener)
            view.keepScreenOn = false
        }
    }
    // Films, episodes and live programmes watched from the start all show a position. Only while
    // the bar shows: nothing else reads it, and it saves a recomposition every half second.
    LaunchedEffect(overlayVisible) {
        while (overlayVisible) {
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
    BackHandler(enabled = state.skipIntroTo != null) { vm.dismissIntro() }
    // The innermost layer first: the controls, then the track list above them.
    BackHandler(enabled = controls) { closeControls(); overlayTick++ }
    BackHandler(enabled = choice != null) {
        val from = choice
        choice = null
        focusTarget = if (from == Choice.AUDIO) buttons.audio else buttons.subtitles
    }
    LaunchedEffect(state.finished) { if (state.finished) onExit() }

    val live = vm.isLive && state.catchup == null
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            // In the controls every key keeps the bar up; the buttons handle the keys themselves.
            .onPreviewKeyEvent { e ->
                if (controls && e.type == KeyEventType.KeyDown) {
                    vm.userActive()
                    overlayTick++
                }
                false
            }
            .onKeyEvent { e ->
                val code = e.key.nativeKeyCode
                // The sleep timer's "still watching?": any key answers it and does nothing else.
                if (state.sleepSecondsLeft != null) {
                    vm.userActive()
                    return@onKeyEvent true
                }
                if (controls || card != null) {
                    // Media keys still work; d-pad and OK belong to the focused control.
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    return@onKeyEvent when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.togglePause(); true }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); true }
                        // Left/Right move between the choices; a row's edges stop focus (see [stayIn]).
                        else -> false
                    }
                }
                // Live: Left is "vorige zender" when released; held, it rewinds (instant replay).
                val liveLeft = live && code == KeyEvent.KEYCODE_DPAD_LEFT && state.sleepSecondsLeft == null && state.breakSecondsLeft == null
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
                vm.userActive()
                val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                if (ok && android.os.SystemClock.uptimeMillis() - cardGoneAt < CARD_GRACE_MS) return@onKeyEvent true
                // Live from the start behaves like a film: pause and seek.
                val handled = if (live) when (code) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.previousChannel(); true }
                    // Down zaps in live, so Right opens the controls, on the audio button.
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { openControls(buttons.audio); true }
                    // First OK shows the info with the restart button; OK while it shows starts it.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (state.replaying) vm.goLive() else if (overlayVisible && state.canRestart) vm.watchFromStart()
                        true
                    }
                    KeyEvent.KEYCODE_INFO -> true
                    else -> false
                } else when (code) {
                    // Pausing hands the keys to the controls, on play: OK again plays on.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        val pausing = vm.player.playWhenReady
                        vm.togglePause()
                        if (pausing) openControls(buttons.play)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); openControls(buttons.play); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(30_000); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { openControls(buttons.play); true }
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
        val barShows = overlayVisible && state.error == null
        val lift = with(density) { if (barShows) barHeight.toDp() else 0.dp }
        Subtitles(vm.player, lift, Modifier.fillMaxSize())

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
            visible = barShows,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = it.height },
        ) {
            PlayerBar(
                state = state,
                live = live,
                catchup = state.catchup,
                position = position,
                duration = duration,
                playing = playWhenReady,
                controls = controls,
                choice = choice,
                buttons = buttons,
                onChoice = { c ->
                    choice = c
                    focusTarget = buttons.firstChoice
                },
                actions = BarActions(
                    replay = { if (live) vm.replay() else vm.seekBy(-10_000) },
                    playPause = vm::togglePause,
                    forward = { vm.seekBy(30_000) },
                    restart = vm::watchFromStart,
                    toLive = { if (state.catchup != null) vm.backToLive() else vm.goLive() },
                    clock = vm::toggleClock,
                    audio = { i -> vm.selectAudio(i); choice = null; focusTarget = buttons.audio },
                    subtitle = { i -> vm.selectSubtitle(i); choice = null; focusTarget = buttons.subtitles },
                ),
            )
        }

        // One card at a time, in the corner every card uses, above the bar when that shows. The key
        // that was down when it came (a seek, a pause) must not also press its button.
        val corner = Modifier.align(Alignment.BottomEnd)
            .padding(start = SafeArea, end = SafeArea, top = SafeArea, bottom = maxOf(SafeArea, lift + 16.dp))
            .ignoreHeldKey()
            .stayIn()
        when (card) {
            Card.SLEEP -> SleepCard(state.sleepSecondsLeft ?: 0, cardFocus, vm::userActive, corner)
            Card.BREAK -> BreakCard(state.breakSecondsLeft ?: 0, cardFocus, vm::endBreak, corner)
            Card.NEXT -> state.nextUp?.let { NextEpisodeCard(it, cardFocus, vm::playNextNow, vm::dismissNextUp, corner) }
            Card.INTRO -> state.skipIntroTo?.let { IntroCard(it, cardFocus, vm::skipIntro, vm::dismissIntro, corner) }
            null -> Unit
        }
    }
}

private enum class Card { SLEEP, BREAK, NEXT, INTRO }

/** Focus can move inside this group but not out of it: a row of choices has edges, not exits. */
private fun Modifier.stayIn() = focusProperties { onExit = { cancelFocusChange() } }.focusGroup()

/** Focus handles of the bar's buttons, so a key can land on the right one. */
private class ControlFocus {
    val play = FocusRequester()
    val audio = FocusRequester()
    val subtitles = FocusRequester()
    /** The selected entry of the open track list. */
    val firstChoice = FocusRequester()
}

private class BarActions(
    val replay: () -> Unit,
    val playPause: () -> Unit,
    val forward: () -> Unit,
    val restart: () -> Unit,
    val toLive: () -> Unit,
    val clock: () -> Unit,
    val audio: (Int) -> Unit,
    val subtitle: (Int?) -> Unit,
)

/**
 * The one control surface: what's on (title, programme, progress), the clock top right, and a row
 * of round controls in remote order (back left, play/pause in the middle, forward right), then
 * audio, subtitles and the clock switch. A track list opens above its own button, in the same bar.
 */
@Composable
private fun PlayerBar(
    state: PlayerUiState,
    live: Boolean,
    catchup: EpgEntry?,
    position: Long,
    duration: Long,
    playing: Boolean,
    controls: Boolean,
    choice: Choice?,
    buttons: ControlFocus,
    onChoice: (Choice) -> Unit,
    actions: BarActions,
) {
    // Where the open list's button sits, to put the list right above it.
    var audioX by remember { mutableFloatStateOf(0f) }
    var subtitlesX by remember { mutableFloatStateOf(0f) }
    var barX by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .padding(start = SafeArea, end = SafeArea, bottom = 32.dp)
            .fillMaxWidth()
            // The pop-up surface, as a panel: the bar, its track lists and the cards are one family.
            .background(PopupSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .onGloballyPositioned { barX = it.positionInRoot().x; barWidth = it.size.width },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (catchup != null || live) state.channelNumber?.let { Text("$it  ", style = MaterialTheme.typography.displaySmall, color = Live) }
            Text(state.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Subtle, level with the title: a glance at the time without a second bar.
            if (state.showClock) ClockText(Modifier.padding(start = 24.dp))
        }
        when {
            catchup != null -> Text("Vanaf begin · ${clock.format(Instant.ofEpochSecond(catchup.start))}  ${catchup.title}", style = MaterialTheme.typography.titleLarge)
            live -> state.now?.let { now ->
                Text("${clock.format(Instant.ofEpochSecond(now.start))}–${clock.format(Instant.ofEpochSecond(now.stop))}  ${now.title}", style = MaterialTheme.typography.titleLarge)
            }
        }
        val fraction = when {
            live -> state.now?.let { (System.currentTimeMillis() / 1000 - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) }
            duration > 0 -> position.toFloat() / duration
            else -> null
        }
        fraction?.let { ProgressBar(it, thickness = 6.dp) }
        if (live) state.next?.let { Text("Straks ${clock.format(Instant.ofEpochSecond(it.start))}  ${it.title}", style = MaterialTheme.typography.bodyLarge, color = TextSecondary) }

        // The open track list, above its button.
        choice?.let { c ->
            val anchor = (if (c == Choice.AUDIO) audioX else subtitlesX) - barX
            TrackList(
                options = if (c == Choice.AUDIO) state.audio else state.subtitles,
                selected = if (c == Choice.AUDIO) state.audioSelected else state.subtitleSelected,
                // Subtitles start with "Uit"; audio has nothing to switch off.
                offLabel = if (c == Choice.SUBTITLES) "Uit" else null,
                emptyLabel = if (c == Choice.AUDIO) "Standaard" else "Geen ondertitels",
                anchorPx = anchor,
                barWidthPx = barWidth,
                first = buttons.firstChoice,
                onPick = { i -> if (c == Choice.AUDIO) i?.let(actions.audio) else actions.subtitle(i) },
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Left: where you are (or, in live, the keys that zap).
            Box(Modifier.weight(1f)) {
                if (live) {
                    Hints(
                        Hint(Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown),
                        Hint(Icons.Filled.KeyboardArrowLeft, text = state.previous),
                    )
                } else {
                    Text(formatTime(position), style = MaterialTheme.typography.titleLarge)
                }
            }
            Row(
                Modifier.focusRestorer(buttons.play).stayIn(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live) {
                    ControlButton(Icons.Filled.Replay10, "10 seconden terug", controls, actions.replay)
                    if (state.replaying) ControlButton(Icons.Filled.LiveTv, "Terug naar live", controls, actions.toLive, Modifier.focusRequester(buttons.play))
                    else if (state.canRestart) ControlButton(Icons.Filled.RestartAlt, "Vanaf begin", controls, actions.restart, Modifier.focusRequester(buttons.play))
                } else {
                    ControlButton(Icons.Filled.Replay10, "10 seconden terug", controls, actions.replay)
                    ControlButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pauze" else "Afspelen", controls, actions.playPause, Modifier.focusRequester(buttons.play))
                    ControlButton(Icons.Filled.Forward30, "30 seconden vooruit", controls, actions.forward)
                    if (catchup != null) ControlButton(Icons.Filled.LiveTv, "Terug naar live", controls, actions.toLive)
                }
                Spacer(Modifier.width(20.dp))
                ControlButton(
                    Icons.Filled.Audiotrack, "Geluid", controls, { onChoice(Choice.AUDIO) },
                    Modifier.focusRequester(buttons.audio).onGloballyPositioned { audioX = it.positionInRoot().x + it.size.width / 2f },
                    active = choice == Choice.AUDIO,
                )
                ControlButton(
                    Icons.Filled.Subtitles, "Ondertitels", controls, { onChoice(Choice.SUBTITLES) },
                    Modifier.focusRequester(buttons.subtitles).onGloballyPositioned { subtitlesX = it.positionInRoot().x + it.size.width / 2f },
                    active = choice == Choice.SUBTITLES || state.subtitleSelected != null,
                )
                ControlButton(Icons.Filled.Schedule, "Klok", controls, actions.clock, active = state.showClock)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (!live && duration > 0) Text(formatTime(duration), style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            }
        }
        state.replayHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Live) }
    }
}

/** A control in the bar: the round icon button of every pop-up, not focusable until the controls are. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** On (clock, subtitles) or open: the icon turns the "selected" blue. */
    active: Boolean = false,
) = IconButton(icon, label, onClick, modifier.focusProperties { canFocus = enabled }, tint = if (active) BrandText else null, large = true)

/**
 * Audio or subtitle tracks, in the bar right above the button that opened it: the pop-up surface
 * with rows that focus like Instellingen, a check at the current one. OK picks and closes it; Back closes it.
 */
@Composable
private fun TrackList(
    options: List<TrackOption>,
    selected: Int?,
    offLabel: String?,
    emptyLabel: String,
    anchorPx: Float,
    barWidthPx: Int,
    first: FocusRequester,
    onPick: (Int?) -> Unit,
) {
    val density = LocalDensity.current
    val width = 320.dp
    val widthPx = with(density) { width.toPx() }
    // Centred on the button, kept inside the bar.
    val x = (anchorPx - widthPx / 2).coerceIn(0f, (barWidthPx - widthPx).coerceAtLeast(0f))
    // (label, index): index null is "off"; an empty list still gets one row, so focus has a place.
    val rows = buildList {
        offLabel?.let { add(it to null) }
        options.forEachIndexed { i, o -> add(o.label to i) }
        if (isEmpty()) add(emptyLabel to null)
    }
    val current = rows.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    Column(
        Modifier
            .offset { IntOffset(x.roundToInt(), 0) }
            .width(width)
            .heightIn(max = 280.dp)
            // The pop-up surface, outlined so it stands apart from the bar it opens in.
            .background(PopupSurface, RoundedCornerShape(16.dp))
            .border(1.dp, FocusFill, RoundedCornerShape(16.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
            .stayIn(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEachIndexed { i, (label, index) ->
            Surface(
                onClick = { onPick(index) },
                modifier = Modifier.fillMaxWidth().then(if (i == current) Modifier.focusRequester(first) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextPrimary,
                    focusedContainerColor = FocusBar,
                    focusedContentColor = TextOnLight,
                    pressedContainerColor = FocusBar,
                    pressedContentColor = TextOnLight,
                ),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (i == current) Icon(Icons.Filled.Check, contentDescription = "Gekozen", tint = LocalContentColor.current, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun ClockText(modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(10_000)
        }
    }
    Text(clock.format(Instant.ofEpochMilli(now)), style = MaterialTheme.typography.headlineMedium, color = TextSecondary, modifier = modifier)
}

/** A key hint that isn't a control: icons, sometimes a word (a channel name). */
private class Hint(vararg val icons: ImageVector, val text: String? = null)

@Composable
private fun Hints(vararg hints: Hint) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { h ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                h.icons.forEach { Icon(it, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(26.dp)) }
                h.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Over the end credits: what's next. One OK plays it; the stop button (or Back) stays here. */
@Composable
private fun NextEpisodeCard(card: NextUpCard, main: FocusRequester, onPlay: () -> Unit, onStop: () -> Unit, modifier: Modifier) {
    CornerCard(
        label = card.secondsLeft?.let { "Volgende aflevering over $it s" } ?: "Kijk je nog?",
        title = card.title,
        modifier = modifier,
        visual = { Text(card.code, style = MaterialTheme.typography.titleMedium, color = TextSecondary) },
    ) {
        PrimaryButton(if (card.secondsLeft == null) "Volgende aflevering" else "Nu kijken", onPlay, Modifier.focusRequester(main), large = true)
        IconButton(Icons.Filled.Stop, "Stoppen", onStop, large = true)
    }
}

/** Pauzemoment, over the paused picture: toilet, drink, snack, and when it goes on. */
@Composable
private fun BreakCard(secondsLeft: Int, main: FocusRequester, onEnd: () -> Unit, modifier: Modifier) {
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
        PrimaryButton("Verder", onEnd, Modifier.focusRequester(main), large = true)
        IconButton(Icons.Filled.SkipNext, "Overslaan", onEnd, large = true)
    }
}

/**
 * Compose has no subtitle renderer yet: Media3's own view, fed with the player's cues. [lift]
 * raises the text above the bar while it shows, so the two never overlap. The view itself gets
 * shorter: many subtitle formats place cues at a fixed fraction of the height, which ignores padding.
 */
@OptIn(UnstableApi::class)
@Composable
private fun Subtitles(player: Player, lift: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val context = LocalContext.current
    val view = remember { SubtitleView(context).apply { setUserDefaultStyle(); setUserDefaultTextSize() } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) = view.setCues(cueGroup.cues)
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    AndroidView({ view }, modifier.padding(bottom = lift))
}

/** Early in an episode of a series whose intro BUIS learned: one OK jumps past it. */
@Composable
private fun IntroCard(to: Long, main: FocusRequester, onSkip: () -> Unit, onWatch: () -> Unit, modifier: Modifier) {
    CornerCard(
        label = "Intro en terugblik · tot ${formatTime(to)}",
        title = "Intro overslaan",
        modifier = modifier,
    ) {
        PrimaryButton("Overslaan", onSkip, Modifier.focusRequester(main), large = true)
        IconButton(Icons.Filled.PlayArrow, "Kijken", onWatch, large = true)
    }
}

/** The sleep timer's question: any key carries on, so does its one button. */
@Composable
private fun SleepCard(secondsLeft: Int, main: FocusRequester, onStay: () -> Unit, modifier: Modifier) {
    CornerCard(
        label = "Slaaptimer · stopt over $secondsLeft s",
        title = "Kijk je nog?",
        modifier = modifier,
    ) {
        PrimaryButton("Doorkijken", onStay, Modifier.focusRequester(main), large = true)
    }
}

internal fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
