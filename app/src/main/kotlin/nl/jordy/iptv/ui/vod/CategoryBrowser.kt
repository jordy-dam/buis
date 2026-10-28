package nl.jordy.iptv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.settings.FilteredCategories
import nl.jordy.iptv.ui.components.CategoryList
import nl.jordy.iptv.ui.theme.TextSecondary

/** Remembers which tile was opened, so Back from the detail screen lands on it again. */
class FocusMemory {
    private var pending: String? = null
    fun opened(key: String) { pending = key }
    /** True exactly once for the remembered tile. */
    fun consume(key: String): Boolean = (pending == key).also { if (it) pending = null }
}

/** Grid posters: four columns fit even while the rail is open. */
val GridPosterWidth: Dp = 96.dp

/**
 * Films and Series, the same way as Live TV: categories (services and genres first) on the left,
 * the whole focused category as a poster grid on the right. A service is a page, not a row to
 * scroll sideways through. [above] goes over the grid (genre chips).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Any> CategoryBrowser(
    sections: List<Pair<String?, List<CategoryEntity>>>?,
    filter: FilteredCategories?,
    onToggleFilter: () -> Unit,
    selected: String?,
    onSelect: (String) -> Unit,
    items: LazyPagingItems<T>,
    itemKey: (T) -> Int,
    focusMemory: FocusMemory,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
    above: @Composable () -> Unit = {},
) {
    val s = sections ?: return
    if (s.isEmpty()) {
        Text("Niets gevonden bij deze provider.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        return
    }
    val listFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    var gridHasFocus by remember { mutableStateOf(false) }
    // Inside the grid (or the chips), Back first returns to the categories; the shell handles the next Back.
    BackHandler(enabled = gridHasFocus) { listFocus.requestFocus() }

    Row(Modifier.fillMaxSize()) {
        CategoryList(
            sections = s,
            selected = selected,
            onFocus = onSelect,
            onClick = { runCatching { gridFocus.requestFocus() } },
            filter = filter,
            onToggleFilter = onToggleFilter,
            modifier = Modifier.width(240.dp).fillMaxHeight().focusRequester(listFocus),
        )
        Column(Modifier.padding(start = 16.dp).fillMaxSize().onFocusChanged { gridHasFocus = it.hasFocus }) {
            above()
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().focusRequester(gridFocus).focusRestorer(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items.itemCount, key = items.itemKey { "$selected/${itemKey(it)}" }) { index ->
                    val item = items[index] ?: return@items
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (focusMemory.consume("$selected/${itemKey(item)}")) runCatching { focus.requestFocus() }
                    }
                    tile(item, Modifier.focusRequester(focus))
                }
            }
        }
    }
}
