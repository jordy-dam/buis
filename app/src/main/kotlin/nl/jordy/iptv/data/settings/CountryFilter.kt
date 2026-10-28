package nl.jordy.iptv.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nl.jordy.iptv.data.db.CategoryEntity

/**
 * Orders and filters a provider's category list around the household's countries.
 *
 * Order: preferred countries, then categories without a country (PPV, Champions League, Multi,
 * radio…), then everything else, each block in the provider's own order. With [onlyPreferred],
 * the last block is hidden: on a 16k-channel provider that removes ~90% of the noise.
 * [hideAdult] (kids profiles) drops adult categories whatever the other settings say.
 */
object CountryFilter {
    fun apply(
        categories: List<CategoryEntity>,
        preferred: Set<String>,
        onlyPreferred: Boolean,
        hideAdult: Boolean = false,
    ): List<CategoryEntity> {
        val visible = if (hideAdult) categories.filterNot { it.adult } else categories
        val (mine, rest) = visible.partition { it.country != null && it.country in preferred }
        val (untagged, foreign) = rest.partition { it.country == null }
        return if (onlyPreferred && mine.isNotEmpty()) mine + untagged else mine + untagged + foreign
    }

    /** Distinct countries in the catalog, preferred first, for the settings picker. */
    fun countries(categories: List<CategoryEntity>, preferred: Set<String>): List<String> =
        categories.mapNotNull { it.country }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy<Map.Entry<String, Int>>({ it.key !in preferred }, { -it.value }))
            .map { it.key }
}

/** A screen's categories after the country filter, plus what the "alle landen" toggle needs. */
data class FilteredCategories(
    val shown: List<CategoryEntity>,
    /** Categories hidden by the filter; 0 when the filter is off or hides nothing. */
    val hidden: Int,
    val showingAll: Boolean,
)

fun Flow<List<CategoryEntity>>.filteredBy(
    prefs: Flow<UserPrefs>,
    showAll: Flow<Boolean>,
): Flow<FilteredCategories> =
    combine(this, prefs, showAll) { cats, p, all ->
        val shown = CountryFilter.apply(cats, p.preferredCountries, onlyPreferred = p.onlyPreferred && !all, hideAdult = p.hideAdult)
        // Adult categories on a kids profile don't count as "more": "Alle landen" won't show them.
        val reachable = if (p.hideAdult) cats.count { !it.adult } else cats.size
        FilteredCategories(shown, hidden = reachable - shown.size, showingAll = all)
    }
