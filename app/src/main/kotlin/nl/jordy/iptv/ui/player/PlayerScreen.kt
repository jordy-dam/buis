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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.ChoiceSize
import nl.jordy.iptv.ui.components.rememberNowAndNext
import nl.jordy.iptv.ui.components.progress
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.SurfaceColor
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import coil3.compose.AsyncImage
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

/** How long the progress line waits after the last Left/Right before it seeks there. */
private const val SCRUB_SETTLE_MS = 800L

/** How long after a pop-up went an OK still counts as meant for it (and is dropped). */
private const val CARD_GRACE_MS = 700L

/** The track list that is open above its button in the bar. */
private enum class Choice { AUDIO, SUBTITLES }

/**
 * The player. Everything you steer sits in one bar at the bottom: title, progress, and a row of round
 * controls that never move. Two modes, never mixed: without the buttons Up/Down zap (live), Left/Right
 * open the bar on the progress line with the first step taken (a film) or on −10/+10 (live), and OK
 * opens it on play/pause, pausing a film at once; a long OK in live opens the channel list. From then
 * on every arrow moves within the bar, Up to the progress line to scrub. Audio and subtitles open
 * their choices in place, above their button. Only Back leaves, the innermost layer first.
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
    // Live: the programme info or the channel list, opened from the bar.
    var panel by remember { mutableStateOf<Panel?>(null) }
    var focusTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val buttons = remember { ControlFocus() }
    // Live OK acts on release: held, it opened the channel list instead. Only a press seen going down counts.
    var okDown by remember { mutableStateOf(false) }
    var okHeld by remember { mutableStateOf(false) }
    // A position picked on the progress line, not yet sought to: the bar shows it instead.
    var scrub by remember { mutableStateOf<Long?>(null) }
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
        panel = null
        controls = false
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(overlayTick, paused, choice, panel) {
        overlayVisible = true
        if (paused || choice != null || panel != null) return@LaunchedEffect
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
    LaunchedEffect(scrub) {
        val to = scrub ?: return@LaunchedEffect
        delay(SCRUB_SETTLE_MS)
        vm.seekBy(to - vm.player.currentPosition)
        position = to
        scrub = null
    }
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
            panel = null
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
    BackHandler(enabled = panel != null) {
        val from = panel
        panel = null
        focusTarget = if (from == Panel.CHANNELS) buttons.channels else buttons.info
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
                    // Media keys still work; d-pad and OK belong to the focused control. The arrows
                    // never leave the bar (an edge just stops, see [stayIn]): only Back does.
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    return@onKeyEvent when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.togglePause(); true }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); true }
                        // The repeats of the arrow that opened the bar, before its target has focus.
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> focusTarget != null
                        else -> false
                    }
                }
                val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                // Live: OK on release opens the controls; held, the channel list.
                if (live && ok) {
                    vm.userActive()
                    if (e.type == KeyEventType.KeyDown) {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            okDown = android.os.SystemClock.uptimeMillis() - cardGoneAt >= CARD_GRACE_MS
                            okHeld = false
                        } else if (okDown && !okHeld) {
                            okHeld = true
                            panel = Panel.CHANNELS
                            openControls(buttons.firstChannel)
                        }
                    } else if (e.type == KeyEventType.KeyUp) {
                        if (okDown && !okHeld) openControls(buttons.play)
                        okDown = false
                    }
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                vm.userActive()
                if (ok && android.os.SystemClock.uptimeMillis() - cardGoneAt < CARD_GRACE_MS) return@onKeyEvent true
                val first = e.nativeKeyEvent.repeatCount == 0
                // Live from the start behaves like a film: pause and scrub.
                val handled = if (live) when (code) {
                    // One zap per press: held, the repeats would flash past half-loaded channels.
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (first) vm.zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (first) vm.zap(-1); true }
                    KeyEvent.KEYCODE_LAST_CHANNEL -> { vm.previousChannel(); true }
                    // Sideways lands next to play: on −10, or on +10 when behind live (else there's no forward).
                    KeyEvent.KEYCODE_DPAD_LEFT -> { openControls(buttons.back); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { openControls(if (state.replaying) buttons.forward else buttons.play); true }
                    // The remote's info key: the programme info, in the controls so Back steps out as usual.
                    KeyEvent.KEYCODE_INFO -> { panel = Panel.INFO; openControls(buttons.info); true }
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
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-10_000); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(30_000); true }
                    // The bar opens on the progress line with the first step already taken; held, the
                    // line takes the repeats and steps faster.
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val length = vm.player.duration
                        if (length > 0) {
                            val step = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 10_000L else -10_000L
                            scrub = (vm.player.currentPosition + step).coerceIn(0, length)
                            openControls(buttons.scrub)
                        } else openControls(buttons.play)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { openControls(buttons.play); true }
                    KeyEvent.KEYCODE_INFO -> true
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
        // The channel list stands on its own: the bar would peek out beside it.
        val barShows = overlayVisible && state.error == null && panel != Panel.CHANNELS
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
                scrub = scrub,
                onScrub = { scrub = it },
                buttons = buttons,
                onChoice = { c ->
                    choice = c
                    focusTarget = buttons.firstChoice
                },
                panel = panel,
                // The info toggles in place; the channel list takes focus, on the channel playing.
                onPanel = { p ->
                    panel = if (panel == p) null else p
                    if (p == Panel.CHANNELS) focusTarget = buttons.firstChannel
                },
                actions = BarActions(
                    back = { if (live) vm.replay() else vm.seekBy(-10_000) },
                    playPause = vm::togglePause,
                    forward = { vm.seekBy(10_000) },
                    restart = { if (live) vm.watchFromStart() else { vm.seekBy(-vm.player.currentPosition); position = 0 } },
                    toLive = { if (state.catchup != null) vm.backToLive() else vm.goLive() },
                    seekTo = { to -> vm.seekBy(to - vm.player.currentPosition); position = to },
                    audio = { i -> vm.selectAudio(i); choice = null; focusTarget = buttons.audio },
                    subtitle = { i -> vm.selectSubtitle(i); choice = null; focusTarget = buttons.subtitles },
                ),
            )
        }

        if (panel == Panel.CHANNELS) {
            ChannelList(
                channels = vm.zapChannels,
                current = vm.currentChannelIndex,
                first = buttons.firstChannel,
                load = vm::nowAndNext,
                onPick = { i -> vm.tune(i); closeControls(); overlayTick++ },
                // A long OK opened it: that OK's repeats and release must not tune in at once.
                modifier = Modifier.align(Alignment.Center).ignoreHeldKey(),
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

private enum class Panel { INFO, CHANNELS }

/** Focus can move inside this group but not out of it: a row of choices has edges, not exits. */
private fun Modifier.stayIn() = focusProperties { onExit = { cancelFocusChange() } }.focusGroup()

/** Like [stayIn], but only the row's ends stop: Up still reaches the progress line above it. */
private fun Modifier.stopsSideways() = focusProperties {
    onExit = { if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange() }
}.focusGroup()

/** Focus handles of the bar's buttons, so a key can land on the right one. */
private class ControlFocus {
    val play = FocusRequester()
    val back = FocusRequester()
    val forward = FocusRequester()
    /** The progress line, where Left/Right in a film land. */
    val scrub = FocusRequester()
    val audio = FocusRequester()
    val subtitles = FocusRequester()
    /** The selected entry of the open track list. */
    val firstChoice = FocusRequester()
    val info = FocusRequester()
    val channels = FocusRequester()
    /** The playing channel in the open channel list. */
    val firstChannel = FocusRequester()
}

private class BarActions(
    val back: () -> Unit,
    val playPause: () -> Unit,
    val forward: () -> Unit,
    val restart: () -> Unit,
    val toLive: () -> Unit,
    /** To a position picked on the progress line. */
    val seekTo: (Long) -> Unit,
    val audio: (Int) -> Unit,
    val subtitle: (Int?) -> Unit,
)

/**
 * The one control surface, slim over a fade: what's on, start and end over a thin progress line, and
 * one row of round controls whose places never change: vanaf begin (or terug naar live) at the far
 * left, −10 / play-pause / +10 in the middle, info, audio and subtitles, and the channel's logo at the
 * far right. What can't be used now dims and is skipped by focus. A track list opens above its own button.
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
    scrub: Long?,
    onScrub: (Long?) -> Unit,
    buttons: ControlFocus,
    onChoice: (Choice) -> Unit,
    panel: Panel?,
    onPanel: (Panel) -> Unit,
    actions: BarActions,
) {
    // Where the open list's button sits, to put the list right above it.
    var audioX by remember { mutableFloatStateOf(0f) }
    var subtitlesX by remember { mutableFloatStateOf(0f) }
    var barX by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableIntStateOf(0) }
    val shown = scrub ?: position
    Column(
        Modifier
            .fillMaxWidth()
            // No panel: a fade over the bottom of the picture, so the bar is text and a line on the image.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.85f)))
            .padding(start = SafeArea, end = SafeArea, top = SafeArea, bottom = 28.dp)
            .onGloballyPositioned { barX = it.positionInRoot().x; barWidth = it.size.width },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The programme info grows the bar upward, on the same fade, instead of a box over the picture.
        if (panel == Panel.INFO) ProgrammeInfo(state, Modifier.padding(bottom = 12.dp))
        // One title: the programme when the guide knows it (the logo button already names the channel).
        val programme = catchup ?: state.now?.takeIf { live }
        Row(verticalAlignment = Alignment.Bottom) {
            if (catchup != null || live) state.channelNumber?.let { Text("$it  ", style = MaterialTheme.typography.displaySmall, color = Live) }
            Text(programme?.title ?: state.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        // Start and end over the ends of the line: guide times, or position and length.
        val (from, to) = when {
            programme != null -> (if (catchup != null) "Vanaf begin · " else "") + clock.format(Instant.ofEpochSecond(programme.start)) to clock.format(Instant.ofEpochSecond(programme.stop))
            !live && duration > 0 -> formatTime(shown) to formatTime(duration)
            else -> null to null
        }
        if (from != null) Row {
            Text(from, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.weight(1f))
            to?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary) }
        }
        val fraction = when {
            live -> state.now?.let { (System.currentTimeMillis() / 1000 - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) }
            duration > 0 -> shown.toFloat() / duration
            else -> null
        }
        // A film or a programme from the start can be scrubbed: Up from the buttons lands on the line.
        if (!live && duration > 0) ScrubLine(
            fraction = fraction ?: 0f,
            enabled = controls,
            onStep = { forward, held ->
                val step = when { held < 8 -> 10_000L; held < 24 -> 30_000L; else -> 120_000L }
                onScrub(((scrub ?: position) + if (forward) step else -step).coerceIn(0, duration))
            },
            onPick = { scrub?.let(actions.seekTo); onScrub(null) },
            modifier = Modifier.focusRequester(buttons.scrub),
        ) else fraction?.let { ProgressBar(it, thickness = 3.dp) }

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

        // Without the controls the arrows steer the picture, so no buttons show. The icons speak for
        // themselves; only the long OK can't be seen, so live says it, buttons or not.
        if (controls) Row(
            Modifier.fillMaxWidth().stopsSideways().ignoreHeldKey(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The far left: back to live when behind it, else from the start (a film always can).
            Box(Modifier.weight(1f)) {
                if (state.replaying || catchup != null) ControlButton(Icons.Filled.LiveTv, "Terug naar live", actions.toLive)
                else ControlButton(Icons.Filled.RestartAlt, "Vanaf begin", actions.restart, available = !live || state.canRestart)
            }
            // The middle: back, play/pause, forward, with play/pause bigger and in the centre of the screen.
            Row(horizontalArrangement = Arrangement.spacedBy(GroupGap), verticalAlignment = Alignment.CenterVertically) {
                ControlButton(Icons.Filled.Replay10, "10 seconden terug", actions.back, Modifier.focusRequester(buttons.back))
                ControlButton(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pauze" else "Afspelen", actions.playPause,
                    Modifier.focusRequester(buttons.play).size(PlaySize),
                )
                // Live, forward only exists behind live.
                ControlButton(Icons.Filled.Forward10, "10 seconden vooruit", actions.forward, Modifier.focusRequester(buttons.forward), available = !live || state.replaying)
            }
            // The right: what's on, how it sounds, the text, and the channel's logo at the far end.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(GroupGap, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                ControlButton(
                    Icons.Filled.Info, "Programma-info", { onPanel(Panel.INFO) },
                    Modifier.focusRequester(buttons.info), active = panel == Panel.INFO, available = live,
                )
                ControlButton(
                    Icons.Filled.Audiotrack, "Geluid", { onChoice(Choice.AUDIO) },
                    Modifier.focusRequester(buttons.audio).onGloballyPositioned { audioX = it.positionInRoot().x + it.size.width / 2f },
                    active = choice == Choice.AUDIO,
                )
                ControlButton(
                    Icons.Filled.Subtitles, "Ondertitels", { onChoice(Choice.SUBTITLES) },
                    Modifier.focusRequester(buttons.subtitles).onGloballyPositioned { subtitlesX = it.positionInRoot().x + it.size.width / 2f },
                    active = choice == Choice.SUBTITLES || state.subtitleSelected != null,
                )
                Spacer(Modifier.width(GroupsGap - GroupGap * 2))
                // The channel's logo opens the channel list, like NLZiet's. A film has no channel: its place stays empty.
                if (live) ChannelButton(state.logoUrl, state.title, { onPanel(Panel.CHANNELS) }, Modifier.focusRequester(buttons.channels))
                else Spacer(Modifier.width(ChoiceSize * 16 / 9))
            }
        }
        if (live) Hints(Hint(text = "Lang OK  Zenderlijst"))
        state.replayHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Live) }
    }
}

/** Space between buttons of one group, and between the groups, so the row reads as its groups. */
private val GroupGap = 12.dp
private val GroupsGap = 48.dp

/** Play/pause, bigger than the rest: where the eye and the focus start. */
private val PlaySize = 64.dp

/**
 * The progress line, focusable in the controls: Left/Right move a picked position (bigger steps the
 * longer the key is held), which is sought to on OK or once the keys rest. Focused, it thickens and
 * shows a knob, so it's clear the arrows now move the position.
 */
@Composable
private fun ScrubLine(fraction: Float, enabled: Boolean, onStep: (forward: Boolean, held: Int) -> Unit, onPick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onStep(false, e.nativeKeyEvent.repeatCount); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onStep(true, e.nativeKeyEvent.repeatCount); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onPick(); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val f = fraction.coerceIn(0f, 1f)
        ProgressBar(f, thickness = if (focused) 6.dp else 3.dp)
        if (focused) Box(Modifier.offset(x = maxWidth * f - 9.dp).size(18.dp).background(TextPrimary, CircleShape))
    }
}

/** A control in the bar: the round icon button of every pop-up. Not available now, it stays in its place, dimmed, and focus skips it. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** On (subtitles) or open: the icon turns the "selected" blue. */
    active: Boolean = false,
    available: Boolean = true,
) = IconButton(
    icon, label, onClick,
    modifier.focusProperties { canFocus = available }.alpha(if (available) 1f else 0.35f),
    tint = if (active) BrandText else null, large = true,
)

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

/** The channel's logo as the bar's last button: a wide rounded plate, so a logo fits, focusable like the round ones. */
@Composable
private fun ChannelButton(
    logoUrl: String?,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(ChoiceSize).width(ChoiceSize * 16 / 9),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Focus.SCALE),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceRaised,
            contentColor = TextSecondary,
            focusedContainerColor = FocusBar,
            focusedContentColor = TextOnLight,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge)
            if (logoUrl != null) {
                AsyncImage(model = logoUrl, contentDescription = "Zenders", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
            }
        }
    }
}

/**
 * NLZiet's "i", at the top of the bar: what the rest of the bar doesn't say. The bar already has the programme, its times and
 * the progress, so this is the time left, what's next and the description. Only to read; Back or the
 * button again closes it.
 */
@Composable
private fun ProgrammeInfo(state: PlayerUiState, modifier: Modifier) {
    Column(
        modifier.width(720.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val now = state.now
        if (now == null) {
            Text("Geen gids voor deze zender.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            return@Column
        }
        val left = ((now.stop - System.currentTimeMillis() / 1000) / 60).coerceAtLeast(0)
        Text("Nog $left min · ${(now.stop - now.start) / 60} min in totaal", style = MaterialTheme.typography.bodyLarge, color = Live)
        state.next?.let { Text("Straks ${clock.format(Instant.ofEpochSecond(it.start))}  ${it.title}", style = MaterialTheme.typography.bodyLarge) }
        Text(
            now.description?.takeIf { it.isNotBlank() } ?: "Geen beschrijving in de gids.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How many channels the list shows at once; Up/Down scroll through the rest. */
private const val CHANNEL_ROWS = 10

/** [ChannelTile]'s height, and the space between two of them. */
private val ChannelRow = 42.dp
private val ChannelRowGap = 4.dp

/**
 * The short channel list over the picture: the channels Up/Down zap through, with what's on, the
 * playing one focused. OK tunes in and closes it; Back closes it.
 */
@Composable
private fun ChannelList(
    channels: List<nl.jordy.iptv.data.db.LiveChannelEntity>,
    current: Int,
    first: FocusRequester,
    load: suspend (Int) -> Pair<EpgEntry?, EpgEntry?>,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    // The playing channel in the middle of the box, so there's as much to see above it as below.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - CHANNEL_ROWS / 2 + 1).coerceAtLeast(0))
    LazyColumn(
        state = listState,
        // A box of ten rows in the middle of the picture, see-through enough to keep watching behind it.
        modifier = modifier.width(600.dp).height(ChannelRow * CHANNEL_ROWS + ChannelRowGap * (CHANNEL_ROWS - 1) + 16.dp)
            .background(SurfaceColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp).stayIn(),
        verticalArrangement = Arrangement.spacedBy(ChannelRowGap),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(channels, key = { _, c -> c.streamId }) { i, c ->
            val (now, _) = rememberNowAndNext(c.streamId, load)
            ChannelTile(
                name = c.name,
                logoUrl = c.logoUrl,
                onClick = { onPick(i) },
                onLongClick = {},
                modifier = if (i == current) Modifier.focusRequester(first) else Modifier,
                number = i + 1,
                now = now,
                container = Color.Transparent,
            )
        }
    }
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
