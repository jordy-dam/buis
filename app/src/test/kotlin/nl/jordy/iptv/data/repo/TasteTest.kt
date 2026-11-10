package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.repo.Taste.DISLIKE
import nl.jordy.iptv.data.repo.Taste.LIKE
import nl.jordy.iptv.data.repo.Taste.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** "Voor jou" must follow what someone said they liked, never what they disliked or already saw. */
class TasteTest {

    private fun film(
        key: String,
        genres: List<String> = emptyList(),
        director: String? = null,
        cast: List<String> = emptyList(),
        rating: Double? = 7.0,
        versions: Int = 1,
        year: Int? = 2010,
        minutes: Int? = null,
    ) = Taste.Film(key, key.hashCode(), key, "https://img/$key.jpg", year, rating, genres, listOfNotNull(director), cast, versions, minutes?.times(60))

    private val inception = film("Inception", listOf("Sciencefiction", "Actie"), "Christopher Nolan", listOf("Leonardo DiCaprio"))
    private val notebook = film("The Notebook", listOf("Romantiek", "Drama"), "Nick Cassavetes", listOf("Ryan Gosling"))

    private val interstellar = film("Interstellar", listOf("Sciencefiction", "Drama"), "Christopher Nolan", listOf("Matthew McConaughey"))
    private val tenet = film("Tenet", listOf("Actie", "Sciencefiction"), "Christopher Nolan")
    private val dearJohn = film("Dear John", listOf("Romantiek", "Drama"), "Lasse Hallström")
    private val laLaLand = film("La La Land", listOf("Romantiek", "Muziek"), "Damien Chazelle", listOf("Ryan Gosling"))
    private val cartoon = film("Cartoon", listOf("Animatie"))
    private val all = listOf(interstellar, tenet, dearJohn, laLaLand, cartoon)

    @Test
    fun `liking a Nolan film and disliking a romance puts Nolan first and romance out`() {
        val got = Taste.suggest(all, listOf(Taste.Vote(inception, LIKE), Taste.Vote(notebook, DISLIKE)), exclude = emptySet())
        val titles = got.map { it.film.title }
        assertEquals("the two Nolan films lead", setOf("Tenet", "Interstellar"), titles.take(2).toSet())
        assertFalse("pure romance from the disliked corner is not suggested", "Dear John" in titles)
        assertFalse("nothing in common with any liked film: no suggestion", "Cartoon" in titles)
    }

    @Test
    fun `the reason names the liked film it has most in common with`() {
        val got = Taste.suggest(all, listOf(Taste.Vote(inception, LIKE), Taste.Vote(notebook, LIKE)), exclude = emptySet())
        assertEquals("Inception", got.first { it.film.key == "Tenet" }.because)
        assertEquals("The Notebook", got.first { it.film.key == "La La Land" }.because)
    }

    @Test
    fun `watched and voted films are never suggested`() {
        val votes = listOf(Taste.Vote(inception, LIKE), Taste.Vote(tenet, DISLIKE))
        val got = Taste.suggest(all + inception, votes, exclude = setOf("Interstellar")).map { it.film.key }
        assertFalse("voted on", "Inception" in got || "Tenet" in got)
        assertFalse("already watched", "Interstellar" in got)
    }

    @Test
    fun `without a single thumb up there is nothing to suggest`() {
        val votes = listOf(Taste.Vote(notebook, DISLIKE), Taste.Vote(inception, UNKNOWN))
        assertTrue(Taste.suggest(all, votes, exclude = emptySet()).isEmpty())
    }

    @Test
    fun `ken ik niet weighs nothing`() {
        assertTrue(Taste.weights(listOf(Taste.Vote(inception, UNKNOWN))).isEmpty())
    }

    @Test
    fun `a film with many genres does not win on count alone`() {
        val liked = film("Liked", listOf("Actie"))
        val focused = film("Focused", listOf("Actie"), rating = 6.5)
        val sprawling = film("Sprawling", listOf("Actie", "Avontuur", "Komedie", "Familie"), rating = 6.5)
        val got = Taste.suggest(listOf(sprawling, focused), listOf(Taste.Vote(liked, LIKE)), emptySet())
        assertEquals("Focused", got.first().film.key)
    }

    @Test
    fun `the provider rating only orders films that share the same taste`() {
        val liked = film("Liked", listOf("Thriller"), "Director X")
        val sameDirectorLow = film("SameDirector", listOf("Thriller"), "Director X", rating = 5.0)
        val genreOnlyTop = film("GenreOnly", listOf("Thriller"), rating = 9.5)
        val got = Taste.suggest(listOf(genreOnlyTop, sameDirectorLow), listOf(Taste.Vote(liked, LIKE)), emptySet())
        assertEquals("SameDirector", got.first().film.key)
    }

    @Test
    fun `a round is four hits, three classics and three more genres, taking turns`() {
        val hits = (1..20).map { film("Hit $it", listOf("Actie"), rating = 7.0, versions = 50 + it, year = 2022) }
        val classics = (1..20).map { film("Classic $it", listOf("Drama"), rating = 8.0 + it / 100.0, versions = 2, year = 1985) }
        val others = listOf("Horror", "Animatie", "Western", "Komedie").flatMap { g -> (1..15).map { film("$g film $it", listOf(g), versions = 5, year = 2012) } }
        val got = Taste.probes(hits + classics + others, emptySet(), random = Random(3), thisYear = 2026)
        assertEquals(10, got.size)
        assertEquals(4, got.count { it.title.startsWith("Hit") })
        assertEquals(3, got.count { it.title.startsWith("Classic") })
        assertEquals("the other three come from genres the round doesn't have yet", 3, got.count { it.genres.first() in setOf("Horror", "Animatie", "Western", "Komedie") })
        assertEquals("kinds take turns, so no four hits in a row", listOf("Hit", "Classic"), got.take(2).map { it.title.substringBefore(' ') })
    }

    @Test
    fun `hits are recent and widely carried, classics old and highly rated`() {
        val filler = (1..15).flatMap { i -> listOf("Actie", "Drama").map { g -> film("$g filler $i", listOf(g), versions = 1, year = 2012, rating = 6.0) } }
        val oldButEverywhere = film("Old everywhere", listOf("Actie"), versions = 99, year = 1999, rating = 7.0)
        val newHit = film("New hit", listOf("Actie"), versions = 40, year = 2023)
        val lowRatedOld = film("Old so-so", listOf("Drama"), rating = 6.5, year = 1990)
        val greatOld = film("Old great", listOf("Drama"), rating = 8.6, year = 1994)
        val concert = film("Live in London 1979", listOf("Muziek"), rating = 9.0, versions = 200, year = 1979)
        val got = Taste.probes(listOf(oldButEverywhere, newHit, lowRatedOld, greatOld, concert) + filler + (1..15).map { film("Muziek $it", listOf("Muziek"), year = 2012, rating = 6.0) }, emptySet(), random = Random(1), thisYear = 2026)
        assertEquals("first comes the hit, then the classic (a feature film, not a concert)", listOf("New hit", "Old great"), got.take(2).map { it.title })
    }

    @Test
    fun `recordings and tv shows with a genre of their own stay out of the round`() {
        val films = (1..20).flatMap { i -> listOf("Actie", "Drama").map { g -> film("$g $i", listOf(g), versions = 3, year = 2015) } }
        val race = film("Formule 1 Miami Race", listOf("F10"), versions = 90, year = 2015)
        val wrestling = (1..20).map { film("AEW Dynamite $it", listOf("TV Film"), versions = 90, year = 2024) }
        val got = Taste.probes(films + race + wrestling, emptySet(), random = Random(4), thisYear = 2026).map { it.title }
        assertEquals(10, got.size)
        assertTrue(got.none { it.startsWith("Formule") || it.startsWith("AEW") })
    }

    @Test
    fun `a round never repeats voted films and needs a poster`() {
        val pool = (1..60).map { film("Film $it", listOf(listOf("Actie", "Drama", "Horror")[it % 3]), versions = it, year = 2015 + it % 10) } +
            film("No poster", listOf("Western"), year = 2024).copy(posterUrl = null)
        val voted = pool.take(10).map { it.key }.toSet()
        val got = Taste.probes(pool, voted, random = Random(2), thisYear = 2026)
        assertEquals(10, got.map { it.key }.distinct().size)
        assertTrue(got.none { it.key in voted || it.posterUrl == null })
    }

    @Test
    fun `liking long films and the nineties nudges, but is never the reason on its own`() {
        val liked = film("Liked", listOf("Drama"), year = 1994, minutes = 170)
        val longNinetiesDrama = film("Long 90s drama", listOf("Drama"), year = 1997, minutes = 160, rating = 6.5)
        val shortNewDrama = film("Short new drama", listOf("Drama"), year = 2021, minutes = 85, rating = 6.5)
        val longNinetiesHorror = film("Long 90s horror", listOf("Horror"), year = 1996, minutes = 150)
        val got = Taste.suggest(listOf(shortNewDrama, longNinetiesDrama, longNinetiesHorror), listOf(Taste.Vote(liked, LIKE)), emptySet()).map { it.film.title }
        assertEquals(listOf("Long 90s drama", "Short new drama"), got)
    }

    @Test
    fun `length and period buckets`() {
        assertEquals("kort", Taste.length(80 * 60))
        assertEquals(null, Taste.length(110 * 60))
        assertEquals("lang", Taste.length(150 * 60))
        assertEquals(null, Taste.length(null))
        assertEquals("1990", Taste.period(1994))
        assertEquals("voor 1980", Taste.period(1962))
        assertEquals(2008, Taste.yearOf(null, "The Dark Knight 4K - 2008"))
        assertEquals(1999, Taste.yearOf(1999, "The Matrix"))
    }

    @Test
    fun `titles in the reason line lose the provider's quality label and year, not a year that is the title`() {
        assertEquals("The Dark Knight", Taste.displayTitle("The Dark Knight 4K - 2008"))
        assertEquals("Frozen II", Taste.displayTitle("Frozen II (2019)"))
        assertEquals("Blade Runner 2049", Taste.displayTitle("Blade Runner 2049"))
        assertEquals("1917", Taste.displayTitle("1917"))
        assertEquals("Raging Bull", Taste.displayTitle("Raging Bull 1980", 1980))
        assertEquals("Blade Runner 2049", Taste.displayTitle("Blade Runner 2049", 2017))
        assertEquals("1917", Taste.displayTitle("1917", 2019))
    }

    @Test
    fun `names split the provider's lists`() {
        assertEquals(listOf("Christopher Nolan", "Emma Thomas"), Taste.names("Christopher Nolan, Emma Thomas"))
        assertEquals(listOf("A B", "C D"), Taste.names("A B / C D"))
        assertTrue(Taste.names(null).isEmpty())
    }
}
