package nl.jordy.iptv.data.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Base64

/**
 * Maps raw Xtream JSON to models. Every function returns null (or skips the item) on bad data
 * instead of throwing: one broken item must never fail a whole sync.
 */
object XtreamParser {

    private val yearInName = Regex("""(?:\(|\s-\s|\s)((?:19|20)\d{2})\)?\s*$""")

    fun account(root: JsonElement): Account {
        val user = root.asObjectOrNull()?.obj("user_info")
        return Account(
            authenticated = user?.int("auth") == 1,
            status = user?.str("status"),
            expiresAt = user?.long("exp_date"),
            maxConnections = user?.int("max_connections"),
            timezone = root.asObjectOrNull()?.obj("server_info")?.str("timezone"),
        )
    }

    fun category(el: JsonElement): Category? {
        val o = el.asObjectOrNull() ?: return null
        val id = o.str("category_id") ?: return null
        return Category(id = id, name = o.str("category_name") ?: "Categorie $id")
    }

    fun liveStream(el: JsonElement): LiveStream? {
        val o = el.asObjectOrNull() ?: return null
        val id = o.int("stream_id") ?: return null
        return LiveStream(
            streamId = id,
            categoryId = o.str("category_id"),
            name = o.str("name") ?: "Zender $id",
            logoUrl = o.url("stream_icon"),
            epgChannelId = o.str("epg_channel_id"),
            number = o.int("num"),
            // Panels that set tv_archive but forget the duration still keep at least a day.
            archiveDays = if (o.int("tv_archive") == 1) (o.int("tv_archive_duration") ?: 1).coerceAtLeast(1) else 0,
        )
    }

    fun movie(el: JsonElement): Movie? {
        val o = el.asObjectOrNull() ?: return null
        val id = o.int("stream_id") ?: return null
        val name = o.str("name") ?: "Film $id"
        return Movie(
            streamId = id,
            categoryId = o.str("category_id"),
            name = name,
            posterUrl = o.url("stream_icon"),
            rating = rating(o),
            year = year(o, name),
            containerExtension = o.str("container_extension") ?: DEFAULT_EXTENSION,
            addedAt = o.long("added"),
            tmdbId = tmdb(o),
        )
    }

    /** Panels spell it `tmdb` or `tmdb_id`, as a number or a string; 0 means none. */
    private fun tmdb(o: JsonObject): Int? = (o.int("tmdb") ?: o.int("tmdb_id"))?.takeIf { it > 0 }

    fun series(el: JsonElement): Series? {
        val o = el.asObjectOrNull() ?: return null
        val id = o.int("series_id") ?: return null
        val name = o.str("name") ?: "Serie $id"
        return Series(
            seriesId = id,
            categoryId = o.str("category_id"),
            name = name,
            coverUrl = o.url("cover"),
            backdropUrl = o.firstUrl("backdrop_path"),
            plot = o.str("plot"),
            genre = o.str("genre"),
            rating = rating(o),
            year = year(o, name),
        )
    }

    fun movieInfo(root: JsonElement): MovieInfo {
        val r = root.asObjectOrNull()
        // `info` is an empty array instead of an object when the provider has no metadata.
        val info = r?.obj("info") ?: JsonObject(emptyMap())
        val data = r?.obj("movie_data") ?: JsonObject(emptyMap())
        return MovieInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            posterUrl = info.url("movie_image") ?: info.url("cover_big"),
            backdropUrl = info.firstUrl("backdrop_path"),
            durationSecs = info.int("duration_secs"),
            rating = rating(info),
            year = year(info, data.str("name").orEmpty()),
            containerExtension = data.str("container_extension"),
            tmdbId = tmdb(info) ?: tmdb(data),
        )
    }

    fun seriesInfo(seriesId: Int, root: JsonElement): SeriesInfo {
        val r = root.asObjectOrNull()
        val info = r?.obj("info") ?: JsonObject(emptyMap())
        // `episodes` is a map of season -> list, or (on some panels) a list of lists.
        val seasonLists: List<Pair<Int?, JsonElement>> = when (val eps = r?.get("episodes")) {
            is JsonObject -> eps.entries.map { (k, v) -> k.toIntOrNull() to v }
            is JsonArray -> eps.mapIndexed { i, v -> (i + 1) to v }
            else -> emptyList()
        }
        val episodes = seasonLists.flatMap { (seasonKey, list) ->
            (list as? JsonArray).orEmpty().mapNotNull { episode(seriesId, seasonKey, it) }
        }.distinctBy { it.episodeId }.sortedWith(compareBy({ it.season }, { it.number }))
        return SeriesInfo(
            plot = info.str("plot"),
            genre = info.str("genre"),
            backdropUrl = info.firstUrl("backdrop_path"),
            episodes = episodes,
        )
    }

    private fun episode(seriesId: Int, seasonKey: Int?, el: JsonElement): Episode? {
        val o = el.asObjectOrNull() ?: return null
        val id = o.int("id") ?: return null
        val season = o.int("season") ?: seasonKey ?: 1
        val number = o.int("episode_num") ?: 0
        val info = o.obj("info") ?: JsonObject(emptyMap())
        return Episode(
            episodeId = id,
            seriesId = seriesId,
            season = season,
            number = number,
            title = o.str("title") ?: "Aflevering $number",
            containerExtension = o.str("container_extension") ?: DEFAULT_EXTENSION,
            plot = info.str("plot"),
            imageUrl = info.url("movie_image"),
            durationSecs = info.int("duration_secs"),
        )
    }

    fun shortEpg(root: JsonElement): List<EpgEntry> {
        val listings = root.asObjectOrNull()?.get("epg_listings") as? JsonArray ?: return emptyList()
        return listings.mapNotNull { el ->
            val o = el.asObjectOrNull() ?: return@mapNotNull null
            val start = o.long("start_timestamp") ?: return@mapNotNull null
            val stop = o.long("stop_timestamp") ?: return@mapNotNull null
            EpgEntry(
                title = decodeBase64(o.str("title")) ?: return@mapNotNull null,
                description = decodeBase64(o.str("description")),
                start = start,
                stop = stop,
            )
        }.sortedBy { it.start }
    }

    /** Short-EPG text is base64; fall back to the raw value when a provider sends plain text. */
    internal fun decodeBase64(raw: String?): String? {
        if (raw == null) return null
        val decoded = runCatching { String(Base64.getDecoder().decode(raw), Charsets.UTF_8) }.getOrNull()
        val text = if (decoded != null && decoded.none { it == '�' || (it.isISOControl() && it != '\n') }) decoded else raw
        return text.trim().takeIf { it.isNotEmpty() }
    }

    /** Prefer the 0–10 `rating`; fall back to `rating_5based` × 2. */
    private fun rating(o: JsonObject): Double? =
        (o.double("rating")?.takeIf { it > 0 && it <= 10 } ?: o.double("rating_5based")?.takeIf { it > 0 && it <= 5 }?.times(2))

    private fun year(o: JsonObject, name: String): Int? {
        val fromField = o.int("year")
            ?: (o.str("releasedate") ?: o.str("releaseDate"))?.take(4)?.toIntOrNull()
        return (fromField ?: yearInName.find(name)?.groupValues?.get(1)?.toIntOrNull())?.takeIf { it in 1900..2100 }
    }

    const val DEFAULT_EXTENSION = "mp4"
}
