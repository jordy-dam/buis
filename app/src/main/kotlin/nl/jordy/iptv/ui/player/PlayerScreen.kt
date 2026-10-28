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
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.ProgressBar
import nl.jordy.iptv.ui.theme.Accent
import nl.jordy.iptv.ui.theme.SafeArea
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

    LaunchedEffect(overlayTick) {
        overlayVisible = true
        delay(if (vm.isLive) 4_000 else 3_000)
        overlayVisible = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    if (!vm.isLive) {
        LaunchedEffect(Unit) {
            while (true) {
                position = vm.player.currentPosition
                duration = vm.player.duration.coerceAtLeast(0)
                delay(500)
            }
        }
    }

    // Home button / screen off: pause and remember the position. Live resumes on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { vm.saveNow(); vm.player.pause() }
                Lifecycle.Event.ON_START -> if (vm.isLive) vm.player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BackHandler { vm.saveNow(); onExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = e.key.nativeKeyCode
                val handled = if (vm.isLive) when (code) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(+1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> true
                    else -> false
                } else when (code) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.togglePause(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(30_000); true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INFO -> true
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
            visible = overlayVisible && state.error == null,
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
                if (vm.isLive) LiveInfo(state) else VodInfo(state.title, position, duration, vm.player.isPlaying)
            }
        }
    }
}

@Composable
private fun LiveInfo(state: nl.jordy.iptv.player.PlayerUiState) {
    Row(verticalAlignment = Alignment.Bottom) {
        state.channelNumber?.let { Text("$it  ", style = MaterialTheme.typography.headlineMedium, color = Accent) }
        Text(state.title, style = MaterialTheme.typography.headlineMedium)
    }
    state.now?.let { now ->
        Text("${clock.format(Instant.ofEpochSecond(now.start))}–${clock.format(Instant.ofEpochSecond(now.stop))}  ${now.title}", style = MaterialTheme.typography.titleLarge)
        val total = (now.stop - now.start).coerceAtLeast(1)
        val elapsed = System.currentTimeMillis() / 1000 - now.start
        ProgressBar(elapsed.toFloat() / total, Modifier.width(480.dp))
    }
    state.next?.let { Text("Straks ${clock.format(Instant.ofEpochSecond(it.start))}  ${it.title}", style = MaterialTheme.typography.bodyLarge, color = TextSecondary) }
    Text("▲▼ andere zender", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
}

@Composable
private fun VodInfo(title: String, position: Long, duration: Long, playing: Boolean) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (duration > 0) ProgressBar(position.toFloat() / duration)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${if (playing) "▶" else "❚❚"}  ${formatTime(position)}", style = MaterialTheme.typography.titleMedium)
        Text("◀ 10s   OK pauze   30s ▶", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(if (duration > 0) formatTime(duration) else "", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
    }
}

internal fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
