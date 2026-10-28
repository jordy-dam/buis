package nl.jordy.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * The built-in avatars: muted colours that stay calm next to the one accent. A profile stores the
 * index; an out-of-range index (fewer colours in a later version) wraps around.
 */
val AvatarColors = listOf(
    Color(0xFF3F7CAC), Color(0xFFB5534B), Color(0xFF4E8F5A), Color(0xFF8A62A8),
    Color(0xFFC08A2E), Color(0xFF2E8C8C), Color(0xFFB0577F), Color(0xFF6B7380),
)

fun avatarColor(index: Int) = AvatarColors[index.mod(AvatarColors.size)]

/** Coloured circle with the name's first letter. No photos and no emoji: emoji ignore the theme. */
@Composable
fun AvatarCircle(name: String, avatar: Int, size: Dp, modifier: Modifier = Modifier, symbol: String? = null) {
    Box(modifier.size(size).background(avatarColor(avatar), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            symbol ?: name.trim().take(1).uppercase().ifEmpty { "?" },
            color = TextPrimary,
            fontSize = (size.value * 0.45f).sp,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/** Focusable avatar with the name underneath, for the profile picker. */
@Composable
fun AvatarTile(
    name: String,
    avatar: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    size: Dp = 128.dp,
    symbol: String? = null,
) {
    Column(Modifier.width(size + 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(size),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Focus.SCALE),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, TextPrimary), shape = CircleShape)),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Focus.glow),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        ) { AvatarCircle(name, avatar, size, symbol = symbol) }
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center) }
    }
}
