package nl.jordy.iptv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * The one card for everything that asks for a moment (a reminder, the next episode, a break, the
 * sleep timer): bottom right at the safe area, same size and style, so people learn one place to
 * look. [label] says what and when in amber ("Over 5 min · 13:00"), [title] says what it's about,
 * [visual] sits on a 16:9 plate on the left, [actions] go underneath: the main choice as a word
 * with focus, the others as round icon buttons, all [ChoiceSize] like the player bar's controls.
 * No box of its own: it slides in from below over [CornerFade], the player bar's fade, so it reads
 * as text on the picture like the bar does. Back is the "no" choice. In the player too: one way for
 * BUIS to ask something.
 */
@Composable
fun CornerCard(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    visual: (@Composable BoxScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    // Slides in once when it appears; a new label or moment on the same card doesn't slide again.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200))) {
    Column(
        // As wide as the content needs, within limits: icon actions keep a card compact. (Plain
        // wrap-content: an intrinsic-width pass measured the card twice on every recomposition.)
        modifier.widthIn(min = 360.dp, max = 560.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (visual != null) {
                Box(
                    Modifier.padding(end = 16.dp).width(112.dp).aspectRatio(16f / 9f).background(SurfaceColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                    content = visual,
                )
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Live)
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
    }
}

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
