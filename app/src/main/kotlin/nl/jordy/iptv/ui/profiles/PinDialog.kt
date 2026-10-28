package nl.jordy.iptv.ui.profiles

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jordy.iptv.data.profile.Pin
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.theme.Accent
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * Four-digit PIN entry for the d-pad: an on-screen number pad, because most Google TV remotes
 * have no digits. Remotes that do have them can type straight away.
 *
 * [onPin] gets the full PIN and returns an error to show (the entry then clears), or null when
 * it's accepted; closing the dialog is up to the caller.
 */
@Composable
fun PinDialog(title: String, onPin: (String) -> String?, onDismiss: () -> Unit, subtitle: String? = null) {
    var digits by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    fun type(d: Char) {
        if (digits.length >= Pin.LENGTH) return
        digits += d
        error = null
        if (digits.length == Pin.LENGTH) {
            error = onPin(digits)
            digits = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(SurfaceColor, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .width(380.dp)
                .onPreviewKeyEvent {
                    val code = it.key.nativeKeyCode
                    if (it.type == KeyEventType.KeyDown && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                        type('0' + (code - KeyEvent.KEYCODE_0)); true
                    } else false
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(Pin.LENGTH) { i ->
                    Box(Modifier.size(18.dp).background(if (i < digits.length) Accent else SurfaceRaised, CircleShape))
                }
            }
            Text(error ?: " ", style = MaterialTheme.typography.bodyMedium, color = Danger)
            // Phone keypad layout: 0 under 8, erase under 7.
            listOf("123", "456", "789", "⌫0 ").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        val m = Modifier.width(PadKey).let { if (c == '1') it.focusRequester(first) else it }
                        when (c) {
                            ' ' -> Box(m)
                            '⌫' -> SecondaryButton("⌫", { digits = digits.dropLast(1) }, m)
                            else -> SecondaryButton(c.toString(), { type(c) }, m)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val PadKey = 72.dp
internal val Danger = androidx.compose.ui.graphics.Color(0xFFFF8A80)
