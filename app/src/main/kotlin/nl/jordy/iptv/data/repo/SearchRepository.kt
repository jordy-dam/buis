package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.MoviePeopleRow
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
    /** Films of a director or with an actor the query names, one row per person. */
    val people: List<PeopleSearch.Person> = emptyList(),
    /** The query is a year or a decade: what's from then. */
    val period: PeriodSearch.Period? = null,
    val periodMovies: List<MovieEntity> = emptyList(),
    val periodSeries: List<SeriesEntity> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() && people.isEmpty() && periodMovies.isEmpty() && periodSeries.isEmpty()
}

/** Without accents and in lower case, split in words: "Penélope Cruz" → [penelope, cruz]. */
private fun words(text: String): List<String> =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }

/** One film per title: language versions share a TMDB id, or else a title and year. */
private fun MovieEntity.key() = tmdbId?.toString() ?: "${name.lowercase()}|$year"

/** Zoeken op regisseur of acteur, from the cast and director in each film's details. */
object PeopleSearch {
    data class Person(val name: String, val director: Boolean, val movies: List<MovieEntity>) {
        val title get() = if (director) "Films van $name" else "Films met $name"
    }

    /** Short queries are titles more often than people: "up", "her". */
    const val MIN_QUERY = 4

    /** The LIKE pattern for the database: its longest word, the rest is checked here. */
    fun pattern(query: String): String? = words(query).maxByOrNull { it.length }?.takeIf { query.trim().length >= MIN_QUERY }?.let { "%$it%" }

    /** Every typed word starts a word of the name: "hanks" and "tom han" find Tom Hanks, "om" doesn't. */
    fun matches(name: String, query: String): Boolean {
        val q = words(query)
        val n = words(name)
        return q.isNotEmpty() && q.all { w -> n.any { it.startsWith(w) } }
    }

    private fun names(field: String?) = field.orEmpty().split(',', ';', '/').map { it.trim() }.filter { it.length > 1 }

    /** The [max] people with the most films, each film once; rows come in preferred order already. */
    fun group(rows: List<MoviePeopleRow>, query: String, max: Int = 3, perPerson: Int = 30): List<Person> {
        class Acc(val name: String) { var directed = 0; var acted = 0; val movies = LinkedHashMap<String, MovieEntity>() }
        val people = LinkedHashMap<String, Acc>()
        for (r in rows) {
            fun add(name: String, director: Boolean) {
                val acc = people.getOrPut(words(name).joinToString(" ")) { Acc(name) }
                if (director) acc.directed++ else acc.acted++
                acc.movies.putIfAbsent(r.movie.key(), r.movie)
            }
            names(r.director).filter { matches(it, query) }.forEach { add(it, director = true) }
            names(r.people).filter { matches(it, query) }.forEach { add(it, director = false) }
        }
        return people.values.sortedByDescending { it.movies.size }.take(max)
            .map { Person(it.name, it.directed > it.acted, it.movies.values.take(perPerson)) }
    }
}

/** Zoeken op periode: a year ("1994"), a decade ("jaren 90", "80s", "1990s") or a span ("2010–2019"). */
object PeriodSearch {
    data class Period(val from: Int, val to: Int, val label: String)

    private val year = Regex("^(19|20)\\d{2}$")
    private val span = Regex("^((?:19|20)\\d{2})\\s*[-–]\\s*((?:19|20)\\d{2})$")
    private val decade = Regex("^(?:jaren\\s*'?|de jaren\\s*'?)?((?:19|20)?\\d0)\\s*'?s?$")

    fun parse(query: String): Period? {
        val q = query.trim().lowercase()
        if (year.matches(q)) return q.toInt().let { Period(it, it, "$it") }
        span.matchEntire(q)?.let { m ->
            val (a, b) = m.destructured.toList().map { it.toInt() }.sorted()
            return Period(a, b, "$a–$b")
        }
        // A bare "90" is a title or a channel number; a decade needs "jaren" or an "s".
        val m = decade.matchEntire(q) ?: return null
        if (!q.startsWith("jaren") && !q.startsWith("de jaren") && !q.endsWith("s")) return null
        val d = m.groupValues[1].toInt()
        val start = when {
            d >= 1900 -> d
            d < 30 -> 2000 + d
            else -> 1900 + d
        }
        return Period(start, start + 9, if (start < 2000) "de jaren ${start % 100}" else "$start–${start + 9}")
    }
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
        val period = PeriodSearch.parse(query)
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
            // One hit per film: language versions of the same title and year are one result.
            movies = ids(ContentType.MOVIE).let { ordered(it, dao.moviesByIds(it)) { m -> m.streamId } }.distinctBy { it.tmdbId?.toString() ?: "${it.name.lowercase()}|${it.year}" },
            series = ids(ContentType.SERIES).let { ordered(it, dao.seriesByIds(it)) { s -> s.seriesId } },
            people = PeopleSearch.pattern(query)?.let { p ->
                PeopleSearch.group(dao.moviesByPerson(p, preferred.toList()).filterNot { hideAdult && it.adult == true }, query)
            }.orEmpty(),
            period = period,
            periodMovies = period?.let { dao.moviesInYears(it.from, it.to, preferred.toList(), hideAdult).distinctBy { m -> m.key() }.take(PER_TYPE) }.orEmpty(),
            periodSeries = period?.let { dao.seriesInYears(it.from, it.to, preferred.toList(), hideAdult).distinctBy { s -> s.name.lowercase() }.take(PER_TYPE) }.orEmpty(),
        )
    }

    private companion object {
        const val PER_TYPE = 30
    }
}
