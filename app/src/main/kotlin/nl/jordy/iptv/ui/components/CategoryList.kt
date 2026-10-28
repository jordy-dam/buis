package nl.jordy.iptv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/**
 * The category column of Live TV, Films and Series. Focusing a category shows it on the right,
 * OK moves there. [sections] may carry headers ("Diensten", "Genres"); headers aren't focusable,
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
) {
    // Entering from the rail lands on the active category, not on whichever row happens to be nearest.
    val selectedFocus = remember { FocusRequester() }
    LazyColumn(
        modifier = modifier.focusRestorer(selectedFocus).focusGroup(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEach { (header, categories) ->
            if (header != null) {
                item(key = "header/$header") {
                    Text(
                        header.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            items(categories, key = { it.id }) { cat ->
                ListItemSurface(
                    text = cat.name,
                    selected = cat.id == selected,
                    onClick = onClick,
                    modifier = Modifier
                        .then(if (cat.id == selected) Modifier.focusRequester(selectedFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocus(cat.id) },
                )
            }
        }
        filterToggle(filter, onToggleFilter)
    }
}
