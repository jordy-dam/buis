package nl.jordy.iptv.ui

import kotlinx.serialization.Serializable

@Serializable data object LoginRoute
@Serializable data object SyncRoute
@Serializable data object ShellRoute
@Serializable data class MovieDetailRoute(val streamId: Int)
@Serializable data class SeriesDetailRoute(val seriesId: Int)

/** What to play. Live carries its category so up/down can zap through the same list. */
@Serializable
data class PlayerRoute(
    val kind: Kind,
    val id: Int,
    val title: String,
    val containerExtension: String = "",
    /** Live: category to zap through ([FAVORITES_CATEGORY] = favourite channels). */
    val categoryId: String? = null,
    val fromStart: Boolean = false,
) {
    enum class Kind { LIVE, MOVIE, EPISODE }

    companion object {
        const val FAVORITES_CATEGORY = "\u0000favorites"
    }
}
