package nl.jordy.iptv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * The one card for everything that asks for a moment (a reminder, the next episode, a break, the
 * sleep timer, bedtime): bottom right at the safe area, right-aligned against the corner, one
 * build, so people learn one place to look. Text only: [label] in amber says what and when, with any countdown in it
 * ("Volgende aflevering over 6 s"), [title] says what it's about, maybe a [subtitle]; [actions] go
 * underneath: the main choice as a word with focus, the others as round icon buttons, all
 * [ChoiceSize] like the player bar's controls. No box, no pictures, no bar: it slides in from the
 * right over [CornerFade], the player bar's fade, so it reads as text on the picture like the bar
 * does. Back is the "no" choice.
 */
@Composable
fun CornerCard(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    // Slides in once when it appears; a new label or moment on the same card doesn't slide again.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    // [modifier] goes on the animation, not the card inside it: an align() from the caller only
    // works on the direct child of its Box. On the card it was ignored and the card sat top left.
    AnimatedVisibility(shown, modifier, enter = slideInHorizontally(tween(280)) { it / 2 } + fadeIn(tween(200))) {
        // Everything leans on the corner: right-aligned and no wider than it needs (at most a
        // narrow column), so a short card sits in the right quarter, not halfway across.
        Column(
            Modifier.widthIn(max = CardMaxWidth).padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Live, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

private val CardMaxWidth = 400.dp

/** The player bar's fade: transparent at the top, dark at the bottom edge, no panel. */
val BottomFade = Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.85f))

/** Behind a [CornerCard]: the bottom of the screen fades, full width, like under the player bar. Put it in a full-screen Box. */
@Composable
fun BoxScope.CornerFade() {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(280))) {
        Box(Modifier.fillMaxWidth().height(460.dp).background(BottomFade))
    }
}
