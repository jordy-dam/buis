package nl.jordy.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextOverflow
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
 * No box: like the player bar, it sits on a soft dark glow over the picture ([softScrim]).
 * Back is the "no" choice. In the player too: one way for BUIS to ask something.
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
    Column(
        // As wide as the content needs, within limits: icon actions keep a card compact. (Plain
        // wrap-content: an intrinsic-width pass measured the card twice on every recomposition.)
        modifier.widthIn(min = 360.dp, max = 560.dp).softScrim().padding(20.dp),
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

/**
 * The pop-ups' backing, like the player bar's fade: a dark glow behind the content that fades out
 * well past it, so there's no edge. Drawn outside the bounds on purpose; nothing here clips.
 */
fun Modifier.softScrim() = drawBehind {
    val spread = 200.dp.toPx()
    val rx = size.width / 2 + spread
    val ry = size.height / 2 + spread
    // A circle squeezed to an ellipse around the content: even fall-off on every side.
    scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to Color.Black.copy(alpha = 0.9f), 0.6f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent, center = center, radius = rx),
            radius = rx,
            center = center,
        )
    }
}
