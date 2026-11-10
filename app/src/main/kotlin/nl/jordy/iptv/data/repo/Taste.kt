package nl.jordy.iptv.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.FavoriteEntity
import nl.jordy.iptv.data.db.TasteFilmRow
import nl.jordy.iptv.data.db.TasteVoteEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.xtream.ContentType
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
    /** "Wil ik zien": heard of it, looks good, can't judge it yet. Goes to Favorieten, weighs a third of a thumb. */
    const val WANT = 2

    private const val LIKE_WEIGHT = 3.0
    private const val DISLIKE_WEIGHT = -2.0
    private const val WANT_WEIGHT = 1.0
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
                WANT -> WANT_WEIGHT
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
        if (votes.none { it.vote == LIKE || it.vote == WANT }) return emptyList()
        // Only a real thumb up is named as the reason: "omdat je X leuk vond" isn't true of a "wil ik zien".
        val liked = votes.filter { it.vote == LIKE }.map { it.film }
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
     * carried widely), [CLASSICS] classics (rated 7.5 or more, from [classicUntil] or before) and
     * the rest from genres the round doesn't cover yet, mixed so the kinds take turns. Each kind is
     * a random pick among its best [POOL], so a second round brings other films. A kind the catalog
     * can't fill is filled from the genre spread.
     */
    fun probes(candidates: List<Film>, votedKeys: Set<String>, count: Int = 10, random: Random = Random.Default, thisYear: Int = java.time.Year.now().value, classicUntil: Int = CLASSIC_UNTIL): List<Film> {
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
            pool.filter { f -> (f.rating ?: 0.0) >= CLASSIC_RATING && f.year != null && f.year <= classicUntil && f.genres.none { it.lowercase() in notFeature } },
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
    /** Series had their great years later (The Wire to Breaking Bad and beyond). */
    const val SERIES_CLASSIC_UNTIL = 2015
    /** Each kind picks at random among its best few, so rounds differ but stay well known. */
    private const val POOL = 15
    private const val MIN_GENRE_FILMS = 15
    /** Titles a genre needs in the catalog to be named in "Mijn smaak". */
    const val REAL_GENRE = 15
    private val notFilms = setOf("tv film", "tv movie", "tv-film")
    private val notFeature = setOf("documentaire", "documentary", "muziek", "music", "reality", "kids", "kinderen", "talk", "news", "nieuws", "soap")

    /** What "Mijn smaak" shows: the strongest pulls and pushes, in words. */
    data class Summary(
        val likes: Int,
        val dislikes: Int,
        val wants: Int,
        val genres: List<String>,
        val avoid: List<String>,
        val directors: List<String>,
        val actors: List<String>,
        val period: String?,
        val length: String?,
    ) {
        val empty get() = likes + dislikes + wants == 0
    }

    /**
     * [realGenres]: genres that are more than a label on a few recordings ("Smackdown", a Greek
     * provider tag); null shows every genre. Weights keep them either way, they're just not worth saying.
     */
    fun summary(votes: List<Vote>, realGenres: Set<String>? = null): Summary {
        val w = weights(votes).filterKeys { k -> !k.startsWith("g:") || realGenres == null || k.removePrefix("g:") in realGenres }
        fun top(prefix: String, n: Int, positive: Boolean = true) = w.entries
            .filter { it.key.startsWith(prefix) && if (positive) it.value > 0 else it.value < 0 }
            .sortedBy { if (positive) -it.value else it.value }
            .take(n).map { label(it.key.removePrefix(prefix)) }
        return Summary(
            likes = votes.count { it.vote == LIKE },
            dislikes = votes.count { it.vote == DISLIKE },
            wants = votes.count { it.vote == WANT },
            genres = top("g:", 5),
            avoid = top("g:", 3, positive = false),
            directors = top("d:", 3),
            actors = top("c:", 3),
            period = top("p:", 1).firstOrNull()?.let { p ->
                // "1990" → "jaren 90"; from 2000 on "jaren 10" is ambiguous, so "2010–2019".
                p.toIntOrNull()?.let { d -> if (d < 2000) "jaren ${d % 100}" else "$d–${d + 9}" } ?: p.lowercase()
            },
            length = top("l:", 1).firstOrNull()?.let { if (it == "Kort") "korte films" else "lange films" },
        )
    }

    /** "christopher nolan" → "Christopher Nolan"; keys are stored lower case. */
    private fun label(key: String) = key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /** The provider's rating as people read it here: "★ 7,8"; null when there is none (0 or missing). */
    fun ratingLabel(rating: Double?): String? =
        rating?.takeIf { it > 0 }?.coerceAtMost(10.0)?.let { "★ " + "%.1f".format(java.util.Locale.forLanguageTag("nl"), it) }

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

    /**
     * "Films voor jou" / "Series voor jou" for the watching profile, recomputed when a vote, the
     * profile's countries or what it watched change (a film seen leaves the row at once). One taste:
     * a thumb for a series counts for films too, and back.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun suggestions(type: ContentType): Flow<List<Taste.Suggestion>> = combine(
        profiles.currentId, profiles.prefs, votes,
        profiles.currentId.flatMapLatest { db.playback().watched(it) },
        // Live zapping writes the log too: only a film or episode started counts, so the row isn't redone per zap.
        profiles.currentId.flatMapLatest { db.watchLog().since(it, 0) }.map { rows -> rows.count { it.type != ContentType.LIVE } }.distinctUntilChanged(),
    ) { id, p, v, _, _ -> Triple(id, p, v) }
        .mapLatest { (id, p, v) ->
            withContext(Dispatchers.Default) {
                if (v.none { it.vote == Taste.LIKE }) return@withContext emptyList()
                Taste.suggest(candidates(type, p.preferredCountries.toList(), p.hideAdult), voted(v), watched(type, id))
            }
        }

    /** Ten films or series to swipe for the watching profile. */
    suspend fun probes(type: ContentType = ContentType.MOVIE, count: Int = 10): List<Taste.Film> = withContext(Dispatchers.Default) {
        val p = profiles.currentPrefs()
        val votedKeys = dao.all(profiles.currentId()).mapTo(HashSet()) { it.filmKey }
        Taste.probes(
            candidates(type, p.preferredCountries.toList(), p.hideAdult), votedKeys, count,
            classicUntil = if (type == ContentType.SERIES) Taste.SERIES_CLASSIC_UNTIL else Taste.CLASSIC_UNTIL,
        )
    }

    suspend fun vote(id: Int, title: String, vote: Int, key: String? = null, type: ContentType = ContentType.MOVIE, imageUrl: String? = null) {
        val profile = profiles.currentId()
        val k = key ?: keyOf(type, id) ?: return
        dao.upsert(TasteVoteEntity(profile, k, id, title, vote, System.currentTimeMillis(), type))
        // "Wil ik zien" is a watchlist too: Favorieten is where this household keeps those.
        if (vote == Taste.WANT) db.favorites().upsert(FavoriteEntity(profile, type, id, title, imageUrl, System.currentTimeMillis()))
    }

    suspend fun hasVoted(id: Int, type: ContentType = ContentType.MOVIE): Boolean {
        val key = keyOf(type, id) ?: return false
        return dao.count(profiles.currentId(), key) > 0
    }

    /** "Mijn smaak" for the watching profile. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: Flow<Taste.Summary> = votes.mapLatest { v ->
        withContext(Dispatchers.Default) {
            val p = profiles.currentPrefs()
            val all = candidates(ContentType.MOVIE, p.preferredCountries.toList(), p.hideAdult) + candidates(ContentType.SERIES, p.preferredCountries.toList(), p.hideAdult)
            val real = all.flatMap { it.genres }.groupingBy { it.lowercase() }.eachCount().filterValues { it >= Taste.REAL_GENRE }.keys
            Taste.summary(voted(v), real)
        }
    }

    /** "Smaak wissen": every thumb of the watching profile. Favorieten from "Wil ik zien" stay. */
    suspend fun reset() = dao.clear(profiles.currentId())

    private suspend fun keyOf(type: ContentType, id: Int) = if (type == ContentType.SERIES) dao.seriesKey(id) else dao.filmKey(id)

    private suspend fun candidates(type: ContentType, countries: List<String>, hideAdult: Boolean) =
        (if (type == ContentType.SERIES) dao.series(countries, hideAdult) else dao.films(countries, hideAdult)).map(Taste::film)

    private suspend fun watched(type: ContentType, profile: Long) =
        (if (type == ContentType.SERIES) dao.watchedSeriesKeys(profile) else dao.watchedKeys(profile)).toSet()

    private suspend fun voted(v: List<TasteVoteEntity>): List<Taste.Vote> {
        val (series, films) = v.partition { it.type == ContentType.SERIES }
        val byId = dao.filmsById(films.map { it.streamId }).associateBy { "m${it.streamId}" } +
            dao.seriesById(series.map { it.streamId }).associateBy { "s${it.streamId}" }
        return v.map { e ->
            // Details that never came still count as voted, so it isn't suggested or swiped again.
            val row = byId[(if (e.type == ContentType.SERIES) "s" else "m") + e.streamId]
            val film = row?.let(Taste::film)?.copy(key = e.filmKey)
                ?: Taste.Film(e.filmKey, e.streamId, e.title, null, null, null, emptyList(), emptyList(), emptyList())
            Taste.Vote(film, e.vote)
        }
    }
}
