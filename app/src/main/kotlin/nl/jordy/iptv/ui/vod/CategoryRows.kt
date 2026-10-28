package nl.jordy.iptv.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import nl.jordy.iptv.data.settings.FilteredCategories
import nl.jordy.iptv.ui.components.filterToggle
import nl.jordy.iptv.ui.components.lastInRow
import nl.jordy.iptv.ui.theme.TextSecondary

/** Remembers which tile was opened, so Back from the detail screen lands on it again. */
class FocusMemory {
    private var pending: String? = null
    fun opened(key: String) { pending = key }
    /** True exactly once for the remembered tile. */
    fun consume(key: String): Boolean = (pending == key).also { if (it) pending = null }
}

/** Netflix pattern: one horizontal row per category, each row paged from Room. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Any> CategoryRows(
    title: String,
    filter: FilteredCategories?,
    onToggleFilter: () -> Unit,
    rowItems: (String) -> Flow<PagingData<T>>,
    itemKey: (T) -> Int,
    focusMemory: FocusMemory,
    tile: @Composable (item: T, categoryId: String, modifier: Modifier) -> Unit,
) {
    val categories = filter?.shown ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineMedium) }
        if (categories.isEmpty()) {
            item { Text("Niets gevonden bij deze provider.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary) }
        }
        items(categories, key = { it.id }) { cat ->
            val paged = remember(cat.id) { rowItems(cat.id) }.collectAsLazyPagingItems()
            Column {
                Text(cat.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                LazyRow(
                    // Room for the 1.1 focus scale, so the focused tile isn't clipped.
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    items(paged.itemCount, key = paged.itemKey { itemKey(it) }) { index ->
                        val item = paged[index] ?: return@items
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            if (focusMemory.consume("${cat.id}/${itemKey(item)}")) runCatching { focus.requestFocus() }
                        }
                        tile(item, cat.id, Modifier.focusRequester(focus).lastInRow(index == paged.itemCount - 1))
                    }
                }
            }
        }
        filterToggle(filter, onToggleFilter)
    }
}
