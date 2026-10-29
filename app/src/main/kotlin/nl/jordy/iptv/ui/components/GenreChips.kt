package nl.jordy.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * "Alle · Drama · Komedie …" above a grid. OK picks a genre; null is "Alle". Films add a length
 * chip in front ([lengthLabel], OK steps it), which stays selected-looking while it filters.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GenreChips(
    genres: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    lengthLabel: String? = null,
    lengthActive: Boolean = false,
    onLength: (() -> Unit)? = null,
) {
    if (genres.isEmpty()) return
    LazyRow(
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (lengthLabel != null && onLength != null) item(key = "length") { Chip(lengthLabel, lengthActive, onClick = onLength) }
        item(key = "all") { Chip("Alle", selected == null) { onSelect(null) } }
        items(genres, key = { it }) { g -> Chip(g, selected == g) { onSelect(g) } }
    }
}

/** A pill-shaped choice: genres above a grid. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = Focus.bar(container = if (selected) Brand else Color.Transparent),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Unspecified else TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
