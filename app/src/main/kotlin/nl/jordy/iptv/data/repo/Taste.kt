package nl.jordy.iptv.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.TasteFilmRow
import nl.jordy.iptv.data.db.TasteVoteEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.xtream.NameParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * "Voor jou", on the device only. Every thumb up adds weight to the film's genres, director and
 * first actors, every thumb down takes some off (the Matchmaker weights: +3 and −2). A film not yet
 * seen scores the sum of its features' weights; the provider's rating only breaks ties.
 *
 * Feature strength: director 1.5, genre 1.0 (shared out over a film's genres, so "Actie, Avontuur,
 * Komedie, Familie" doesn't win on count), each of the first [CAST] actors 0.6. Length (kort under
 * 95 min, lang over 140) and period (decade, "voor 1980" before that) weigh 0.5 each, and only
 * nudge: a film needs a liked genre, director or actor to be suggested at all, so "also from the
 * nineties" alone never is a reason.
 */
object Taste {
    const val LIKE = 1
    const val DISLIKE = -1
    const val UNKNOWN = 0

    private const val LIKE_WEIGHT = 3.0
    private const val DISLIKE_WEIGHT = -2.0
    private const val DIRECTOR = 1.5
    private const val ACTOR = 0.6
    private const val CAST = 4
    private const val LENGTH = 0.5
    private const val PERIOD = 0.5
    const val SHORT_SECS = 95 * 60
    const val LONG_SECS = 140 * 60
    /** Per rating point above or below 6.5: enough to order equals, never to beat a shared feature. */
    private const val RATING = 0.15

    data class Film(
        val key: String,
        val streamId: Int,
        val title: String,
        val posterUrl: String?,
        val year: Int?,
        val rating: Double?,
        val genres: List<String>,
        val directors: List<String>,
        val cast: List<String>,
        val versions: Int = 1,
        val durationSecs: Int? = null,
    )

    data class Vote(val film: Film, val vote: Int)

    data class Suggestion(val film: Film, val score: Double, val because: String?)

    /** "kort", "lang" or null for the middle (and unknown). */
    fun length(secs: Int?): String? = when {
        secs == null || secs <= 0 -> null
        secs < SHORT_SECS -> "kort"
        secs > LONG_SECS -> "lang"
        else -> null
    }

    /** "1990", "2010"… per decade; everything before 1980 is one period. */
    fun period(year: Int?): String? = when {
        year == null || year < 1900 -> null
        year < 1980 -> "voor 1980"
        else -> "${year / 10 * 10}"
    }

    /** Feature → strength for one film. */
    fun features(f: Film): Map<String, Double> = buildMap {
        val g = if (f.genres.isEmpty()) 0.0 else 1.0 / sqrt(f.genres.size.toDouble())
        f.genres.forEach { put("g:${it.lowercase()}", g) }
        f.directors.forEach { put("d:${it.lowercase()}", DIRECTOR) }
        f.cast.take(CAST).forEach { put("c:${it.lowercase()}", ACTOR) }
        length(f.durationSecs)?.let { put("l:$it", LENGTH) }
        period(f.year)?.let { put("p:$it", PERIOD) }
    }

    /** Genre, director, actors: what a film is, as opposed to how long or how old. */
    private fun isCore(key: String) = key.startsWith("g:") || key.startsWith("d:") || key.startsWith("c:")

    /** The profile's taste: feature → weight from all its thumbs. "Ken ik niet" adds nothing. */
    fun weights(votes: List<Vote>): Map<String, Double> {
        val w = HashMap<String, Double>()
        for (v in votes) {
            val sign = when (v.vote) {
                LIKE -> LIKE_WEIGHT
                DISLIKE -> DISLIKE_WEIGHT
                else -> continue
            }
            features(v.film).forEach { (k, s) -> w[k] = (w[k] ?: 0.0) + sign * s }
        }
        return w
    }

    fun score(f: Film, weights: Map<String, Double>): Double {
        val taste = features(f).entries.sumOf { (k, s) -> (weights[k] ?: 0.0) * s }
        val r = f.rating?.takeIf { it > 0 }?.let { (it.coerceAtMost(10.0) - 6.5) * RATING } ?: 0.0
        return taste + r
    }

    /**
     * The best films not voted on and not in [exclude] (watched or started). Only films that share
     * a genre, director or actor the profile liked: with nothing to go on there is no suggestion.
     */
    fun suggest(candidates: List<Film>, votes: List<Vote>, exclude: Set<String>, limit: Int = 20): List<Suggestion> {
        val liked = votes.filter { it.vote == LIKE }.map { it.film }
        if (liked.isEmpty()) return emptyList()
        val weights = weights(votes)
        val voted = votes.mapTo(HashSet()) { it.film.key }
        return candidates.asSequence()
            .filter { it.key !in voted && it.key !in exclude }
            .mapNotNull { f ->
                val positive = features(f).entries.filter { isCore(it.key) }.sumOf { (k, s) -> ((weights[k] ?: 0.0) * s).coerceAtLeast(0.0) }
                if (positive <= 0.0) return@mapNotNull null
                val s = score(f, weights)
                if (s <= 0.0) null else f to s
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (f, s) -> Suggestion(f, s, because(f, liked)) }
            .toList()
    }

    /** The liked film this one has most in common with (genre, director, actors), for "Omdat je X leuk vond". */
    fun because(f: Film, liked: List<Film>): String? {
        val mine = features(f).filterKeys(::isCore)
        return liked.map { l -> l to features(l).entries.sumOf { (k, s) -> (mine[k] ?: 0.0) * s } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first?.title
    }

    /**
     * Ten films for "Leer mijn smaak", already voted ones never again: [HITS] hits (recent and
     * carried widely), [CLASSICS] classics (rated 7.5 or more, from [CLASSIC_UNTIL] or before) and
     * the rest from genres the round doesn't cover yet, mixed so the kinds take turns. Each kind is
     * a random pick among its best [POOL], so a second round brings other films. A kind the catalog
     * can't fill is filled from the genre spread.
     */
    fun probes(candidates: List<Film>, votedKeys: Set<String>, count: Int = 10, random: Random = Random.Default, thisYear: Int = java.time.Year.now().value): List<Film> {
        // Real genres only, for every kind: a "genre" with a handful of titles is a recording ("F10",
        // "Kids"), and "TV Film" is mostly shows (wrestling), not films anyone has an opinion about.
        val usable = candidates.filter { it.key !in votedKeys && it.genres.isNotEmpty() && it.posterUrl != null && (it.rating ?: 0.0) >= 6.0 }
        val genreSize = usable.groupingBy { it.genres.first().lowercase() }.eachCount()
        val pool = usable.filter { f -> f.genres.none { it.lowercase() in notFilms } && (genreSize[f.genres.first().lowercase()] ?: 0) >= MIN_GENRE_FILMS }
        val seen = HashSet<String>()
        fun best(films: List<Film>, order: Comparator<Film>, n: Int): List<Film> =
            films.filter { it.key !in seen }.sortedWith(order).take(POOL).shuffled(random).take(n).onEach { seen += it.key }

        val hits = best(
            pool.filter { (it.year ?: 0) >= thisYear - HIT_YEARS },
            compareByDescending<Film> { it.versions }.thenByDescending { it.rating ?: 0.0 }, HITS,
        )
        val classics = best(
            // A classic is a feature film: high-rated documentaries and concert recordings aren't.
            pool.filter { f -> (f.rating ?: 0.0) >= CLASSIC_RATING && f.year != null && f.year <= CLASSIC_UNTIL && f.genres.none { it.lowercase() in notFeature } },
            // Widely carried first: the highest rated old films are often obscure ones with few votes.
            compareByDescending<Film> { it.versions }.thenByDescending { it.rating ?: 0.0 }, CLASSICS,
        )
        // The rest: one film per genre the round has no film of yet, then any genre.
        val covered = (hits + classics).flatMap { it.genres }.mapTo(HashSet()) { it.lowercase() }
        val known = compareByDescending<Film> { it.versions }.thenByDescending { it.rating ?: 0.0 }
        val spread = mutableListOf<Film>()
        val byGenre = pool.groupBy { it.genres.first().lowercase() }
        val genres = byGenre.keys.filter { it !in covered }.shuffled(random) + byGenre.keys.filter { it in covered }.shuffled(random)
        // Round after round over the genres, until the ten are there or the pool is used up.
        while (hits.size + classics.size + spread.size < count) {
            val before = spread.size
            for (g in genres) {
                if (hits.size + classics.size + spread.size >= count) break
                spread += best(byGenre.getValue(g), known, 1)
            }
            if (spread.size == before) break
        }
        // Take turns: hit, classic, genre, hit, …
        val queues = listOf(hits.toMutableList(), classics.toMutableList(), spread.toMutableList())
        val picked = mutableListOf<Film>()
        while (picked.size < count && queues.any { it.isNotEmpty() }) {
            for (q in queues) {
                q.removeFirstOrNull()?.let { picked += it }
                if (picked.size == count) break
            }
        }
        return picked
    }

    private const val HITS = 4
    private const val CLASSICS = 3
    /** A hit came out in the last ten years. */
    private const val HIT_YEARS = 10
    private const val CLASSIC_RATING = 7.5
    const val CLASSIC_UNTIL = 2005
    /** Each kind picks at random among its best few, so rounds differ but stay well known. */
    private const val POOL = 15
    private const val MIN_GENRE_FILMS = 15
    private val notFilms = setOf("tv film", "tv movie", "tv-film")
    private val notFeature = setOf("documentaire", "documentary", "muziek", "music")

    /** "The Dark Knight 4K - 2008" → "The Dark Knight": the provider's labels and year don't belong in a sentence. */
    fun displayTitle(raw: String, year: Int? = null): String {
        val clean = NameParser.parse(raw.replace(trailingYear, "")).title.replace(trailingYear, "").trim().ifEmpty { raw }
        // "Raging Bull 1980" with year 1980: the year shows next to it already. "Blade Runner 2049" (2017) keeps it.
        return if (year != null && clean.endsWith(" $year")) clean.removeSuffix(" $year").trim() else clean
    }

    private val trailingYear = Regex("""\s*[-(]\s*(19|20)\d{2}\)?\s*$""")

    /** "Christopher Nolan, Emma Thomas" → names; providers separate with commas, sometimes slashes. */
    fun names(raw: String?): List<String> = raw.orEmpty().split(',', '/', '|').map { it.trim() }.filter { it.length >= 2 }.distinct()

    /** The provider's year, or the one at the end of its title ("The Dark Knight 4K - 2008"). */
    fun yearOf(year: Int?, title: String): Int? = year?.takeIf { it > 1900 }
        ?: trailingYear.find(title)?.value?.filter(Char::isDigit)?.toIntOrNull()

    fun film(r: TasteFilmRow) = Film(
        key = r.filmKey, streamId = r.streamId, title = r.name, posterUrl = r.posterUrl, year = yearOf(r.year, r.name), rating = r.rating,
        genres = Genres.split(r.genre), directors = names(r.director), cast = names(r.cast), versions = r.versions, durationSecs = r.durationSecs,
    )
}

@Singleton
class TasteRepository @Inject constructor(
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
) {
    private val dao = db.taste()

    @OptIn(ExperimentalCoroutinesApi::class)
    val votes: Flow<List<TasteVoteEntity>> = profiles.currentId.flatMapLatest { dao.observe(it) }

    /** "Voor jou" for the watching profile; recomputed when a vote or the profile's countries change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: Flow<List<Taste.Suggestion>> = combine(profiles.currentId, profiles.prefs, votes) { id, p, v -> Triple(id, p, v) }
        .mapLatest { (id, p, v) ->
            withContext(Dispatchers.Default) {
                if (v.none { it.vote == Taste.LIKE }) return@withContext emptyList()
                val films = dao.films(p.preferredCountries.toList(), p.hideAdult).map(Taste::film)
                Taste.suggest(films, voted(v), dao.watchedKeys(id).toSet())
            }
        }

    /** Ten films to swipe for the watching profile. */
    suspend fun probes(count: Int = 10): List<Taste.Film> = withContext(Dispatchers.Default) {
        val p = profiles.currentPrefs()
        val votedKeys = dao.all(profiles.currentId()).mapTo(HashSet()) { it.filmKey }
        Taste.probes(dao.films(p.preferredCountries.toList(), p.hideAdult).map(Taste::film), votedKeys, count)
    }

    suspend fun vote(streamId: Int, title: String, vote: Int, key: String? = null) {
        val profile = profiles.currentId()
        val filmKey = key ?: dao.filmKey(streamId) ?: return
        dao.upsert(TasteVoteEntity(profile, filmKey, streamId, title, vote, System.currentTimeMillis()))
    }

    suspend fun hasVoted(streamId: Int): Boolean {
        val key = dao.filmKey(streamId) ?: return false
        return dao.count(profiles.currentId(), key) > 0
    }

    private suspend fun voted(v: List<TasteVoteEntity>): List<Taste.Vote> {
        val byId = dao.filmsById(v.map { it.streamId }).associateBy { it.streamId }
        return v.mapNotNull { e ->
            // A film whose details never came still counts as voted, so it isn't suggested or swiped again.
            val film = byId[e.streamId]?.let(Taste::film)?.copy(key = e.filmKey)
                ?: Taste.Film(e.filmKey, e.streamId, e.title, null, null, null, emptyList(), emptyList(), emptyList())
            Taste.Vote(film, e.vote)
        }
    }
}
