package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.SearchEntity
import nl.jordy.iptv.data.db.SeriesEntity
import nl.jordy.iptv.data.xtream.ContentType
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val channels: List<LiveChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

object SearchRanking {
    /**
     * FTS4 MATCH expression from what the user typed: every word must match the start of a word
     * in the title ("npo 1" → `npo* 1*`). Anything that isn't a letter or digit is dropped, so
     * typed quotes or operators can't break the query.
     */
    fun matchExpression(query: String): String? {
        val words = Normalizer.normalize(query.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
        return words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
    }

    /**
     * Preferred countries first, then titles that start with the query, then shorter titles
     * (the closest match), each item once even if it sits in several categories.
     */
    fun rank(hits: List<SearchEntity>, query: String, preferred: Set<String>, perType: Int): Map<String, List<SearchEntity>> {
        val q = query.trim().lowercase()
        return hits
            .distinctBy { it.type to it.itemId }
            .sortedWith(
                compareBy<SearchEntity>(
                    { it.country == null || it.country !in preferred },
                    { !it.title.lowercase().startsWith(q) },
                    { it.title.length },
                ),
            )
            .groupBy { it.type }
            .mapValues { (_, v) -> v.take(perType) }
    }
}

@Singleton
class SearchRepository @Inject constructor(private val db: AppDatabase) {

    /** [hideAdult]: kids profile, hits that only sit in adult categories are dropped. */
    suspend fun search(query: String, preferred: Set<String>, hideAdult: Boolean = false): SearchResults {
        val match = SearchRanking.matchExpression(query) ?: return SearchResults()
        val dao = db.catalog()
        var hits = db.search().match(match, preferred.toList(), limit = 600)
        if (hideAdult) {
            val adult = ContentType.entries.associateWith { dao.adultCategoryIds(it).toSet() }
            hits = hits.filterNot { h -> ContentType.valueOf(h.type).let { h.categoryId in adult.getValue(it) } }
        }
        // Over-fetch, then rank: FTS returns hits in index order, not by relevance.
        val ranked = SearchRanking.rank(hits, query, preferred, perType = PER_TYPE)
        fun ids(type: ContentType) = ranked[type.name].orEmpty().map { it.itemId }
        fun <T> ordered(ids: List<Int>, rows: List<T>, id: (T) -> Int) = rows.associateBy(id).let { m -> ids.mapNotNull { m[it] } }
        return SearchResults(
            channels = ids(ContentType.LIVE).let { ordered(it, dao.liveByIds(it)) { c -> c.streamId } },
            movies = ids(ContentType.MOVIE).let { ordered(it, dao.moviesByIds(it)) { m -> m.streamId } },
            series = ids(ContentType.SERIES).let { ordered(it, dao.seriesByIds(it)) { s -> s.seriesId } },
        )
    }

    private companion object {
        const val PER_TYPE = 30
    }
}
