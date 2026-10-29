package nl.jordy.iptv.ui.profiles

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin

/** ↑ ↑ ↓ ↓ ← → ← → OK, the classic code. Keys in between that don't fit start it over. */
class TestbeeldCode {
    private var at = 0

    /** True when [keyCode] completes the code. */
    fun feed(keyCode: Int): Boolean {
        at = when {
            keyCode == SEQUENCE[at] -> at + 1
            keyCode == SEQUENCE[0] -> 1
            else -> 0
        }
        if (at == SEQUENCE.size) {
            at = 0
            return true
        }
        return false
    }

    companion object {
        val SEQUENCE = listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}

// The PM5544-ish bars, left to right.
private val bars = listOf(
    Color(0xFFC0C0C0), Color(0xFFC0C000), Color(0xFF00C0C0), Color(0xFF00C000),
    Color(0xFFC000C0), Color(0xFFC00000), Color(0xFF0000C0),
)
private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The easter egg: the picture switches off like an old tube (it collapses into a line, then a dot),
 * comes back as a testbeeld with the real time and a soft 1 kHz tone, and any key warms the tube
 * up again into the app.
 */
@Composable
fun TestbeeldOverlay(onDone: () -> Unit) {
    // 1 = full picture, towards 0 the tube switches off: first the height, then the width.
    val height = remember { Animatable(1f) }
    val width = remember { Animatable(1f) }
    var showBars by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        height.animateTo(0.004f, tween(220))
        width.animateTo(0f, tween(260))
        delay(500)
        showBars = true
        width.animateTo(1f, tween(200))
        height.animateTo(1f, tween(320))
    }
    if (showBars && !leaving) Tone()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && !leaving && showBars) {
                    leaving = true
                    scope.launch {
                        height.animateTo(0.004f, tween(200))
                        width.animateTo(0f, tween(220))
                        delay(250)
                        onDone()
                    }
                }
                // Nothing reaches the picker underneath while the testbeeld is up, Back included.
                e.key.nativeKeyCode != KeyEvent.KEYCODE_HOME
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * width.value.coerceAtLeast(0.006f)
            val h = size.height * height.value
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            if (!showBars) {
                // The collapsing picture is just white light.
                drawRect(Color.White, Offset(left, top), Size(w, h))
                return@Canvas
            }
            val barW = w / bars.size
            bars.forEachIndexed { i, c -> drawRect(c, Offset(left + i * barW, top), Size(barW + 1, h * 0.72f)) }
            // Lower strip: black to white steps, the other half of a test card.
            val steps = 6
            val stepW = w / steps
            for (i in 0 until steps) {
                val v = i / (steps - 1f)
                drawRect(Color(v, v, v), Offset(left + i * stepW, top + h * 0.72f), Size(stepW + 1, h * 0.28f))
            }
            drawCircle(Color.White, radius = h * 0.36f, center = Offset(size.width / 2, size.height / 2), style = Stroke(width = 4f))
            drawLine(Color.White, Offset(size.width / 2, top), Offset(size.width / 2, top + h), strokeWidth = 2f)
            drawLine(Color.White, Offset(left, size.height / 2), Offset(left + w, size.height / 2), strokeWidth = 2f)
        }
        if (showBars && height.value > 0.9f && !leaving) {
            val now by produceState(LocalTime.now()) {
                while (true) {
                    value = LocalTime.now()
                    delay(1_000)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BUIS", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.background(Color.Black).padding(horizontal = 28.dp, vertical = 4.dp))
                Text(clock.format(now), fontSize = 28.sp, color = Color.White, modifier = Modifier.background(Color.Black).padding(horizontal = 16.dp, vertical = 2.dp).padding(top = 0.dp))
            }
            Text(
                "Beste Unieke IPTV Stream",
                fontSize = 22.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).background(Color.Black).padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
    }
}

/** A soft 1 kHz test tone while the testbeeld shows: 48 samples per cycle at 48 kHz loops without a click. */
@Composable
private fun Tone() {
    DisposableEffect(Unit) {
        val rate = 48_000
        val samples = ShortArray(4_800) { i -> (sin(2 * PI * 1_000 * i / rate) * Short.MAX_VALUE * 0.08).toInt().toShort() }
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
                .apply {
                    write(samples, 0, samples.size)
                    setLoopPoints(0, samples.size, -1)
                    play()
                }
        }.getOrNull()
        onDispose {
            runCatching { track?.stop() }
            track?.release()
        }
    }
}
