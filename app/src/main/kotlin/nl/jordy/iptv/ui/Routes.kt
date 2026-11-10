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
/** Zenderstatus: channels that hiccup and their replacements. */
@Serializable data object ChannelHealthRoute
@Serializable data class SeriesDetailRoute(val seriesId: Int)
/** "Gids": the timeline grid of one of the profile's guides. */
@Serializable data class TvGuideRoute(val guideId: Long)
/** "Leer mijn smaak": swipe ten films (or series) for "Films voor jou" / "Series voor jou". */
@Serializable data class TasteRoute(val series: Boolean = false)
/** Instellingen → Mijn smaak: what the thumbs add up to, and wiping them. */
@Serializable data object TasteProfileRoute
/** Zoeken from outside the rail, with a query filled in (a director or actor from Mijn smaak). */
@Serializable data class SearchRoute(val query: String)

/** What to play. Live carries its category so up/down can zap through the same list. */
@Serializable
data class PlayerRoute(
    val kind: Kind,
    val id: Int,
    val title: String,
    val containerExtension: String = "",
    /** Live: category to zap through, or a profile's guide ([guideCategory]). */
    val categoryId: String? = null,
    val fromStart: Boolean = false,
    /** Live with [fromStart]: a programme that already ended (from the guide grid); 0 = the one on now. */
    val catchupStart: Long = 0,
    val catchupStop: Long = 0,
    val catchupTitle: String = "",
) {
    enum class Kind { LIVE, MOVIE, EPISODE }

    companion object {
        private const val GUIDE_PREFIX = "\u0000guide:"

        /** Zapping through one of the profile's guides instead of a provider category. */
        fun guideCategory(guideId: Long) = "$GUIDE_PREFIX$guideId"

        fun guideId(categoryId: String?): Long? = categoryId?.removePrefix(GUIDE_PREFIX)?.takeIf { it != categoryId }?.toLongOrNull()
    }
}
