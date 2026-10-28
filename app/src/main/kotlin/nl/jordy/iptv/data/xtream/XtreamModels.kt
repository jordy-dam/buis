package nl.jordy.iptv.data.xtream

enum class ContentType { LIVE, MOVIE, SERIES }

data class Account(
    val authenticated: Boolean,
    val status: String?,
    /** Epoch seconds, null when the subscription has no expiry. */
    val expiresAt: Long?,
    val maxConnections: Int?,
)

data class Category(val id: String, val name: String)

data class LiveStream(
    val streamId: Int,
    val categoryId: String?,
    val name: String,
    val logoUrl: String?,
    val epgChannelId: String?,
    val number: Int?,
)

data class Movie(
    val streamId: Int,
    val categoryId: String?,
    val name: String,
    val posterUrl: String?,
    /** 0–10 scale. */
    val rating: Double?,
    val year: Int?,
    val containerExtension: String,
    val addedAt: Long?,
    /** The film's TMDB id when the panel sends one; the same for every language version. */
    val tmdbId: Int? = null,
)

data class Series(
    val seriesId: Int,
    val categoryId: String?,
    val name: String,
    val coverUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val rating: Double?,
    val year: Int?,
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val durationSecs: Int?,
    val rating: Double?,
    val year: Int?,
    val containerExtension: String?,
    val tmdbId: Int? = null,
)

data class Episode(
    val episodeId: Int,
    val seriesId: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val containerExtension: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSecs: Int?,
)

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val backdropUrl: String?,
    val episodes: List<Episode>,
)

data class EpgEntry(val title: String, val description: String?, val start: Long, val stop: Long)
