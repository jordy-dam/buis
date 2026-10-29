package nl.jordy.iptv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.settings.FilteredCategories
import nl.jordy.iptv.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import nl.jordy.iptv.data.settings.CategoryGroups
import nl.jordy.iptv.ui.theme.Brand
import nl.jordy.iptv.ui.theme.BrandText

/**
 * The category column of Live TV, Films and Series. Focusing a category shows it on the right,
 * OK moves there. [sections] may carry headers ("Vastgezet", "Diensten", "Genres"); headers aren't focusable,
 * so the d-pad steps straight over them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryList(
    sections: List<Pair<String?, List<CategoryEntity>>>,
    selected: String?,
    onFocus: (String) -> Unit,
    onClick: () -> Unit,
    filter: FilteredCategories?,
    onToggleFilter: () -> Unit,
    modifier: Modifier = Modifier,
    /** Long-press or Menu on a category (Films/Series: pin it). */
    onLongClick: ((String) -> Unit)? = null,
) {
    // Entering from the rail lands on the active category, not on whichever row happens to be nearest.
    val selectedFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Pinning moves the focused category to the top, out of view: follow it, or focus is lost.
    var follow by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sections) {
        val id = follow ?: return@LaunchedEffect
        follow = null
        // Same item count as below: the pinned panel is one item, other sections a header plus a row each.
        var index = 0
        for ((header, categories) in sections) {
            val at = categories.indexOfFirst { it.id == id }
            val target = when {
                at < 0 -> null
                header == PINNED -> index
                else -> index + at // the row before it (or the header), so it isn't glued to the top edge
            }
            if (target != null) {
                listState.scrollToItem(target)
                withFrameNanos { }
                runCatching { selectedFocus.requestFocus() }
                return@LaunchedEffect
            }
            index += if (header == PINNED) 1 else categories.size + (if (header != null) 1 else 0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer(selectedFocus).focusGroup(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun row(cat: CategoryEntity) = ListItemSurface(
            text = cat.name,
            tags = categoryTags(cat.badges),
            selected = cat.id == selected,
            onClick = onClick,
            onLongClick = onLongClick?.let { { follow = cat.id; it(cat.id) } },
            modifier = Modifier
                .then(if (cat.id == selected) Modifier.focusRequester(selectedFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocus(cat.id) },
        )

        sections.forEach { (header, categories) ->
            // The profile's own pins sit on one light-blue panel, so they read as theirs, apart from the catalog.
            if (header == PINNED) {
                item(key = "pinned") {
                    Column(
                        Modifier.fillMaxWidth().background(Brand.copy(alpha = 0.16f), RoundedCornerShape(10.dp)).padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SectionTitle(header, BrandText)
                        categories.forEach { row(it) }
                    }
                }
                return@forEach
            }
            if (header != null) {
                item(key = "header/$header") { SectionTitle(header, TextSecondary) }
            }
            items(categories, key = { it.id }) { row(it) }
        }
        filterToggle(filter, onToggleFilter)
    }
}

private val PINNED = CategoryGroups.Kind.PINNED.title

@Composable
private fun SectionTitle(title: String, color: Color) = Text(
    title.uppercase(),
    style = MaterialTheme.typography.labelLarge,
    color = color,
    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
)
