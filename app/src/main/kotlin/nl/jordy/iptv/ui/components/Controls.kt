package nl.jordy.iptv.ui.components

import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.tv.material3.LocalTextStyle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
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
import nl.jordy.iptv.ui.theme.FocusBar
import nl.jordy.iptv.ui.theme.FocusFill
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextOnLight
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

/**
 * The choices of a pop-up and the player bar are one size bigger than buttons on a screen: they're
 * read from the couch over a picture. [PrimaryButton] and [IconButton] take `large` for them.
 */
val ChoiceSize = 48.dp

/** A 20sp line, its text centred in it (not sitting on the line's proportional baseline). */
private val LargeLabel
    @Composable get() = MaterialTheme.typography.titleMedium.copy(
        lineHeight = 20.sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
        ),
    )

/** 14 + the 20dp [LargeLabel] line + 14 = [ChoiceSize]. */
private val LargePadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp)

/**
 * Looks the same as [SecondaryButton]: a coloured button next to the focused one read as a second
 * focus, so only focus (the light fill) says where you are. Primary is where focus starts.
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, large: Boolean = false) =
    OutlineButton(text, onClick, modifier, enabled, large)

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) =
    OutlineButton(text, onClick, modifier, enabled = true, large = large)

/**
 * One base for both buttons so they have identical height: with mismatched heights the taller
 * button counts as "below" the other, and d-pad Down jumps sideways to it.
 */
@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, large: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        // Taller by padding, not by height(): the button lays its content row at the top, so a
        // fixed height would leave the label sitting high.
        contentPadding = if (large) LargePadding else ButtonDefaults.ContentPadding,
        scale = ButtonDefaults.scale(focusedScale = Focus.SCALE),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, TextSecondary), shape = CircleShape),
            focusedBorder = Border.None,
        ),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = TextPrimary,
            // Buttons always focus light, on a screen and over the picture; rows keep [FocusFill].
            focusedContainerColor = FocusBar,
            focusedContentColor = TextOnLight,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContentColor = TextSecondary,
        ),
    ) { Text(text, style = if (large) LargeLabel else LocalTextStyle.current) }
}

/**
 * A round button with only an icon, for secondary actions everyone recognises (restart, close,
 * settings). Same height as [PrimaryButton] so d-pad Left/Right stays on one row; [label] is read
 * out by accessibility services.
 */
@Composable
fun IconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A colour that means something (red heart for a favourite); default follows the button. */
    tint: androidx.compose.ui.graphics.Color? = null,
    /** [ChoiceSize], for pop-ups and the player bar. */
    large: Boolean = false,
) {
    val icon = if (large) 26.dp else 20.dp
    Button(
        onClick = onClick,
        // Square, so the circle is a circle; as high as PrimaryButton of the same size.
        modifier = modifier.size(if (large) ChoiceSize else 40.dp),
        shape = ButtonDefaults.shape(CircleShape),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        scale = ButtonDefaults.scale(focusedScale = Focus.SCALE),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, TextSecondary), shape = CircleShape),
            focusedBorder = Border.None,
        ),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = FocusBar,
            focusedContentColor = TextOnLight,
        ),
    ) {
        // Centred in the whole circle (the button's content row would put it at the start).
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.tv.material3.Icon(imageVector, contentDescription = label, tint = tint ?: androidx.tv.material3.LocalContentColor.current, modifier = Modifier.size(icon))
        }
    }
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

/** The heart: outlined to add, filled red when it's a favourite. */
@Composable
fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) = IconButton(
    if (favorite) androidx.compose.material.icons.Icons.Filled.Favorite else androidx.compose.material.icons.Icons.Filled.FavoriteBorder,
    if (favorite) "In favorieten" else "Favoriet",
    onToggle,
    modifier,
    tint = if (favorite) nl.jordy.iptv.ui.theme.Heart else null,
)
