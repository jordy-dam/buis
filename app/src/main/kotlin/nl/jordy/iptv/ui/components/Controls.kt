package nl.jordy.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.FocusFill
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * Text field for the d-pad. The outer surface takes focus like any other control; only OK puts
 * the inner field in edit mode and opens the keyboard. Without this, moving down through the
 * form pops the keyboard at every field and traps the d-pad.
 */
@Composable
fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    /** Open the keyboard the first time the field gets focus (search), instead of waiting for OK. */
    editOnFirstFocus: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val outer = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var outerFocused by remember { mutableStateOf(false) }
    var focusedBefore by remember { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            inner.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        Surface(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth().focusRequester(outer).onFocusChanged {
                outerFocused = it.hasFocus
                if (it.isFocused && editOnFirstFocus && !focusedBefore) editing = true
                if (it.hasFocus) focusedBefore = true
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, SurfaceRaised), shape = RoundedCornerShape(8.dp)),
                focusedBorder = Border(BorderStroke(2.dp, FocusBar), shape = RoundedCornerShape(8.dp)),
            ),
            colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceColor, focusedContainerColor = SurfaceColor),
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    cursorBrush = SolidColor(TextPrimary),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onAny = {
                        keyboard?.hide()
                        editing = false
                        outer.requestFocus()
                        onImeAction()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inner)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing && !outerFocused) editing = false },
                )
            }
        }
    }
}

/** The primary button one step lighter when focused, like every other focus. */
private val BrandFocus = androidx.compose.ui.graphics.Color(0xFF3F7FC2)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = Focus.SCALE),
        colors = ButtonDefaults.colors(
            containerColor = Brand,
            contentColor = TextPrimary,
            focusedContainerColor = BrandFocus,
            focusedContentColor = TextPrimary,
        ),
    ) { Text(text) }
}

/**
 * Same base as [PrimaryButton] so both have identical height: with mismatched heights the taller
 * button counts as "below" the other, and d-pad Down jumps sideways to it.
 */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = Focus.SCALE),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, TextSecondary), shape = CircleShape),
            focusedBorder = Border.None,
        ),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = FocusFill,
            focusedContentColor = TextPrimary,
        ),
    ) { Text(text) }
}

/**
 * For a dialog opened by a long-press: the OK that opened it is still held, and its repeats and
 * release would land on the dialog's focused button and confirm it at once. Those are swallowed;
 * the dialog reacts from the first fresh key press on.
 */
@Composable
fun Modifier.ignoreHeldKey(): Modifier {
    var armed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { e ->
        if (armed) return@onPreviewKeyEvent false
        val fresh = e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0
        if (fresh) {
            armed = true
            false
        } else {
            if (e.type == KeyEventType.KeyUp) armed = true
            true
        }
    }
}
