package nl.jordy.iptv.ui.profiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextOnLight
import nl.jordy.iptv.ui.theme.TextPrimary
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
                .padding(28.dp)
                .width(380.dp)
                .onPreviewKeyEvent {
                    val code = it.key.nativeKeyCode
                    if (it.type == KeyEventType.KeyDown && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                        type('0' + (code - KeyEvent.KEYCODE_0)); true
                    } else false
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(Pin.LENGTH) { i ->
                    Box(Modifier.size(18.dp).background(if (i < digits.length) TextPrimary else SurfaceRaised, CircleShape))
                }
            }
            Text(error ?: " ", style = MaterialTheme.typography.bodyMedium, color = Danger)
            // Phone keypad layout: 0 under 8, erase under 7.
            Column(verticalArrangement = Arrangement.spacedBy(PadGap)) {
                listOf("123", "456", "789", "⌫0 ").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PadGap)) {
                        row.forEach { c ->
                            val m = if (c == '1') Modifier.focusRequester(first) else Modifier
                            when (c) {
                                ' ' -> Box(Modifier.size(PadKey))
                                '⌫' -> PadKey("", { digits = digits.dropLast(1) }, m, icon = Icons.AutoMirrored.Filled.Backspace)
                                else -> PadKey(c.toString(), { type(c) }, m)
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** A round key with its digit in the middle: outlined at rest, filled like other buttons with focus. */
@Composable
private fun PadKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(PadKey),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Focus.SCALE),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, TextSecondary), shape = CircleShape),
            focusedBorder = Border.None,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = FocusBar,
            focusedContentColor = TextOnLight,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) androidx.tv.material3.Icon(icon, contentDescription = "Wissen", modifier = Modifier.size(24.dp))
            else Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

// Sized so the whole dialog fits the 540dp-high tv screen with its title, dots and error line.
private val PadKey = 60.dp
private val PadGap = 12.dp
internal val Danger = androidx.compose.ui.graphics.Color(0xFFFF8A80)
