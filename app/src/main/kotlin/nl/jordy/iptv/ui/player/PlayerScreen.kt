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
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
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
 * The player. Everything you steer sits in one bar at the bottom: title, clock, progress, and a row
 * of round controls. Two modes, never mixed: without the buttons the arrows steer the picture (VOD:
 * Left/Right seek; live: Up/Down zap, Left previous channel) and the bar says so; OK (or Down in a
 * film, or a pause) brings the buttons, and from then on every arrow moves within the bar, Up to the
 * progress line to scrub. Audio and subtitles open their choices in place, above their button. Only
 * Back leaves, the innermost layer first: the choices, then the controls.
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
                    // The arrows zap, so OK alone brings the controls: on vanaf begin / terug naar live
                    // when there is one, else on the channel logo. Right only shows the bar.
                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        openControls(if (state.replaying || state.canRestart) buttons.play else buttons.channels)
                        true
                    }
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
                    replay = { if (live) vm.replay() else vm.seekBy(-10_000) },
                    playPause = vm::togglePause,
                    forward = { vm.seekBy(30_000) },
                    restart = vm::watchFromStart,
                    toLive = { if (state.catchup != null) vm.backToLive() else vm.goLive() },
                    clock = vm::toggleClock,
                    seekTo = { to -> vm.seekBy(to - vm.player.currentPosition); position = to },
                    audio = { i -> vm.selectAudio(i); choice = null; focusTarget = buttons.audio },
                    subtitle = { i -> vm.selectSubtitle(i); choice = null; focusTarget = buttons.subtitles },
                ),
            )
        }

        if (barShows && panel == Panel.INFO) {
            ProgrammeInfo(
                state,
                Modifier.align(Alignment.BottomStart).padding(start = SafeArea, bottom = lift + 16.dp),
            )
        }
        if (panel == Panel.CHANNELS) {
            ChannelList(
                channels = vm.zapChannels,
                current = vm.currentChannelIndex,
                first = buttons.firstChannel,
                load = vm::nowAndNext,
                onPick = { i -> vm.tune(i); closeControls(); overlayTick++ },
                modifier = Modifier.align(Alignment.CenterStart),
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
    val replay: () -> Unit,
    val playPause: () -> Unit,
    val forward: () -> Unit,
    val restart: () -> Unit,
    val toLive: () -> Unit,
    val clock: () -> Unit,
    /** To a position picked on the progress line. */
    val seekTo: (Long) -> Unit,
    val audio: (Int) -> Unit,
    val subtitle: (Int?) -> Unit,
)

/**
 * The one control surface, slim over a fade: what's on, start and end over a thin progress line, the
 * clock top right, and a row of round controls in remote order (back left, play/pause in the middle,
 * forward right), then audio, subtitles and the clock switch. A track list opens above its own button, in the same bar.
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
    panel: Panel?,
    onPanel: (Panel) -> Unit,
    actions: BarActions,
) {
    // Where the open list's button sits, to put the list right above it.
    var audioX by remember { mutableFloatStateOf(0f) }
    var subtitlesX by remember { mutableFloatStateOf(0f) }
    var barX by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableIntStateOf(0) }
    // The focused control's name: icons alone don't say what OK will do.
    var focused by remember { mutableStateOf<String?>(null) }
    val named: (String, Boolean) -> Unit = { label, has -> if (has) focused = label else if (focused == label) focused = null }
    // A position picked on the progress line, not yet sought to: the bar shows it instead.
    var scrub by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(scrub) {
        val to = scrub ?: return@LaunchedEffect
        delay(SCRUB_SETTLE_MS)
        actions.seekTo(to)
        scrub = null
    }
    val shown = scrub ?: position
    Column(
        Modifier
            .fillMaxWidth()
            // No panel: a fade over the bottom of the picture, so the bar is text and a line on the image.
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
            .padding(start = SafeArea, end = SafeArea, top = SafeArea, bottom = 28.dp)
            .onGloballyPositioned { barX = it.positionInRoot().x; barWidth = it.size.width },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // One title: the programme when the guide knows it (the logo button already names the channel).
        val programme = catchup ?: state.now?.takeIf { live }
        Row(verticalAlignment = Alignment.Bottom) {
            if (catchup != null || live) state.channelNumber?.let { Text("$it  ", style = MaterialTheme.typography.displaySmall, color = Live) }
            Text(programme?.title ?: state.title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Subtle, level with the title: a glance at the time without a second bar.
            if (state.showClock) ClockText(Modifier.padding(start = 24.dp))
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
                scrub = ((scrub ?: position) + if (forward) step else -step).coerceIn(0, duration)
            },
            onPick = { scrub?.let(actions.seekTo); scrub = null },
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

        // Without the controls the arrows steer the picture, so no buttons show: just what they do.
        if (!controls) Hints(
            *if (live) listOfNotNull(
                Hint(Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown, text = "Zappen"),
                state.previous?.let { Hint(Icons.Filled.KeyboardArrowLeft, text = it) },
                Hint(text = "OK  Bediening"),
            ).toTypedArray() else arrayOf(
                Hint(Icons.Filled.KeyboardArrowLeft, Icons.Filled.KeyboardArrowRight, text = "Spoelen"),
                Hint(text = "OK  Pauze en bediening"),
            ),
        ) else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.focusRestorer(buttons.play).stopsSideways(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live) {
                    // The channel's logo opens the channel list, like NLZiet's.
                    ChannelButton(
                        state.logoUrl, state.title, controls, named, { onPanel(Panel.CHANNELS) },
                        Modifier.focusRequester(buttons.channels),
                    )
                    ControlButton(Icons.Filled.Replay10, "10 seconden terug", controls, named, actions.replay)
                    if (state.replaying) ControlButton(Icons.Filled.LiveTv, "Terug naar live", controls, named, actions.toLive, Modifier.focusRequester(buttons.play))
                    else if (state.canRestart) ControlButton(Icons.Filled.RestartAlt, "Vanaf begin", controls, named, actions.restart, Modifier.focusRequester(buttons.play))
                } else {
                    ControlButton(Icons.Filled.Replay10, "10 seconden terug", controls, named, actions.replay)
                    ControlButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pauze" else "Afspelen", controls, named, actions.playPause, Modifier.focusRequester(buttons.play))
                    ControlButton(Icons.Filled.Forward30, "30 seconden vooruit", controls, named, actions.forward)
                    if (catchup != null) ControlButton(Icons.Filled.LiveTv, "Terug naar live", controls, named, actions.toLive)
                }
                Spacer(Modifier.width(20.dp))
                ControlButton(
                    Icons.Filled.Audiotrack, "Geluid", controls, named, { onChoice(Choice.AUDIO) },
                    Modifier.focusRequester(buttons.audio).onGloballyPositioned { audioX = it.positionInRoot().x + it.size.width / 2f },
                    active = choice == Choice.AUDIO,
                )
                ControlButton(
                    Icons.Filled.Subtitles, "Ondertitels", controls, named, { onChoice(Choice.SUBTITLES) },
                    Modifier.focusRequester(buttons.subtitles).onGloballyPositioned { subtitlesX = it.positionInRoot().x + it.size.width / 2f },
                    active = choice == Choice.SUBTITLES || state.subtitleSelected != null,
                )
                ControlButton(Icons.Filled.Schedule, "Klok", controls, named, actions.clock, active = state.showClock)
                if (live) {
                    ControlButton(
                        Icons.Filled.Info, "Programma-info", controls, named, { onPanel(Panel.INFO) },
                        Modifier.focusRequester(buttons.info), active = panel == Panel.INFO,
                    )
                }
            }
            // Right of the buttons: the focused one's name.
            Box(Modifier.weight(1f).padding(start = 24.dp)) {
                focused?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            }
        }
        state.replayHint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Live) }
    }
}

/**
 * The progress line, focusable in the controls: Left/Right move a picked position (bigger steps the
 * longer the key is held), which is sought to on OK or once the keys rest. Focused, it thickens and
 * shows a knob, so it's clear the arrows now move the position.
 */
@Composable
private fun ScrubLine(fraction: Float, enabled: Boolean, onStep: (forward: Boolean, held: Int) -> Unit, onPick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
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

/** A control in the bar: the round icon button of every pop-up, not focusable until the controls are. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    /** Tells the bar this button's name while it has focus. */
    named: (String, Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** On (clock, subtitles) or open: the icon turns the "selected" blue. */
    active: Boolean = false,
) = IconButton(
    icon, label, onClick,
    modifier.focusProperties { canFocus = enabled }.onFocusChanged { named(label, it.isFocused) },
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

/** The channel's logo as the bar's first button: a wide rounded plate, so a logo fits, focusable like the round ones. */
@Composable
private fun ChannelButton(
    logoUrl: String?,
    name: String,
    enabled: Boolean,
    named: (String, Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(ChoiceSize).width(ChoiceSize * 16 / 9)
            .focusProperties { canFocus = enabled }.onFocusChanged { named("Zenders", it.isFocused) },
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
 * NLZiet's "i": what the bar under it doesn't say. The bar already has the programme, its times and
 * the progress, so this is the time left, what's next and the description. Only to read; Back or the
 * button again closes it.
 */
@Composable
private fun ProgrammeInfo(state: PlayerUiState, modifier: Modifier) {
    Column(
        modifier.width(720.dp).background(PopupSurface, RoundedCornerShape(16.dp)).border(1.dp, FocusFill, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val now = state.now
        if (now == null) {
            Text("Geen gids voor deze zender.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            return@Column
        }
        val left = ((now.stop - System.currentTimeMillis() / 1000) / 60).coerceAtLeast(0)
        Text("Nog $left min · ${(now.stop - now.start) / 60} min in totaal", style = MaterialTheme.typography.labelLarge, color = Live)
        state.next?.let { Text("Straks ${clock.format(Instant.ofEpochSecond(it.start))}  ${it.title}", style = MaterialTheme.typography.bodyLarge) }
        Text(
            now.description?.takeIf { it.isNotBlank() } ?: "Geen beschrijving in de gids.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis,
        )
    }
}

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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight().width(720.dp).background(SurfaceColor.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp).stayIn(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = SafeArea),
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
