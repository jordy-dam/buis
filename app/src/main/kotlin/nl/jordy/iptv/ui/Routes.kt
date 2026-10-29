package nl.jordy.iptv.ui

import kotlinx.serialization.Serializable

@Serializable data object LoginRoute
@Serializable data object SyncRoute
@Serializable data object ShellRoute

/**
 * "Wie kijkt er?". [atLaunch]: shown on start, Back exits and a pick continues to [syncAfter]
 * (stale catalog) or the shell. Otherwise opened from the rail: a pick or Back returns there.
 */
@Serializable data class ProfilesRoute(val atLaunch: Boolean, val syncAfter: Boolean = false)

/** Add (id 0) or edit a profile. */
@Serializable data class ProfileEditRoute(val id: Long = 0)
@Serializable data class MovieDetailRoute(val streamId: Int)
/** Kijkgeschiedenis and the weekoverzicht of the active profile. */
@Serializable data object HistoryRoute
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
