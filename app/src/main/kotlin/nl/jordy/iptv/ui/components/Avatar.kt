package nl.jordy.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
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
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.SurfaceRaised
import nl.jordy.iptv.ui.theme.TextPrimary
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
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

/**
 * Coloured circle with a drawn [face] (see [AvatarFaces]) or, for face 0, the name's first letter.
 * No photos and no emoji: emoji ignore the theme.
 */
@Composable
fun AvatarCircle(name: String, avatar: Int, size: Dp, modifier: Modifier = Modifier, symbol: String? = null, face: Int = 0) {
    Box(modifier.size(size).background(avatarColor(avatar), CircleShape), contentAlignment = Alignment.Center) {
        val drawn = AvatarFaces.get(face).takeIf { symbol == null }
        if (drawn != null) {
            Image(drawn, contentDescription = null, colorFilter = ColorFilter.tint(TextPrimary), modifier = Modifier.size(size * 0.68f))
            return@Box
        }
        Text(
            symbol ?: name.trim().take(1).uppercase().ifEmpty { "?" },
            color = TextPrimary,
            fontSize = (size.value * 0.45f).sp,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

/**
 * Focusable avatar with the name underneath, for the profile picker. [badge] marks the profile on
 * the circle itself (kids, PIN) instead of a line of text, so every tile has the same height.
 */
@Composable
fun AvatarTile(
    name: String,
    avatar: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    symbol: String? = null,
    face: Int = 0,
    badge: ImageVector? = null,
    badgeDescription: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // Scaled as a whole, so the badge stays on the circle's edge when it grows with focus.
    val scale by animateFloatAsState(if (focused) Focus.SCALE else 1f, label = "avatar")
    Column(Modifier.width(size + 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Surface(
                onClick = onClick,
                modifier = modifier.size(size).onFocusChanged { focused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, TextPrimary), shape = CircleShape)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            ) { AvatarCircle(name, avatar, size, symbol = symbol, face = face) }
            badge?.let { AvatarBadge(it, badgeDescription, size) }
        }
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * A small dark disc on the circle's lower right edge (at 45°, where a corner badge would fall off
 * the round avatar), ringed in the page colour so it reads on every avatar colour.
 */
@Composable
private fun AvatarBadge(icon: ImageVector, description: String?, avatar: Dp) {
    val badge = avatar * 0.34f
    // The point on the circle at 45°: r + r·cos 45° from the top left, minus half the badge.
    val at = avatar * 0.854f - badge / 2
    Box(
        Modifier
            .offset(at, at)
            .size(badge)
            .background(Background, CircleShape)
            .padding(2.dp)
            .background(SurfaceRaised, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(badge * 0.6f))
    }
}
