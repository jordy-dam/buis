package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.MoviePeopleRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zoeken op regisseur, acteur en periode: what people type must land on the right films. */
class PeopleAndPeriodSearchTest {

    private fun film(id: Int, name: String, tmdb: Int? = null, year: Int? = 2000) =
        MovieEntity("1", id, name, null, null, year, "mkv", null, id, 1, tmdbId = tmdb)

    private fun row(m: MovieEntity, director: String?, cast: String?) = MoviePeopleRow(m, cast, director, false)

    @Test
    fun `names match on word starts, without accents, in any order`() {
        assertTrue(PeopleSearch.matches("Tom Hanks", "hanks"))
        assertTrue(PeopleSearch.matches("Tom Hanks", "hanks tom"))
        assertTrue(PeopleSearch.matches("Penélope Cruz", "penelope"))
        assertFalse("inside a word is not a match", PeopleSearch.matches("Tom Hanks", "anks"))
        assertNull("too short to be a person", PeopleSearch.pattern("tom"))
    }

    @Test
    fun `a person gets one row, each film once, director or actor by what they mostly did`() {
        val rows = listOf(
            row(film(1, "Cast Away", tmdb = 8358), "Robert Zemeckis", "Tom Hanks, Helen Hunt"),
            row(film(2, "Cast Away (NL)", tmdb = 8358), "Robert Zemeckis", "Tom Hanks, Helen Hunt"), // other language version
            row(film(3, "Big"), "Penny Marshall", "Tom Hanks"),
            row(film(4, "Forrest Gump"), "Robert Zemeckis", "Tom Hanks"),
            row(film(5, "Top Gun"), "Tony Scott", "Tom Cruise"),
        )
        val hanks = PeopleSearch.group(rows, "tom hanks")
        assertEquals(listOf("Tom Hanks"), hanks.map { it.name })
        assertEquals("Films met Tom Hanks", hanks[0].title)
        assertEquals(listOf(1, 3, 4), hanks[0].movies.map { it.streamId })

        val zemeckis = PeopleSearch.group(rows, "zemeckis").single()
        assertEquals("Films van Robert Zemeckis", zemeckis.title)

        assertEquals("most films first", listOf("Tom Hanks", "Tom Cruise"), PeopleSearch.group(rows, "tom").map { it.name })
    }

    @Test
    fun `years, decades and spans are periods, a bare number is not`() {
        assertEquals(PeriodSearch.Period(1994, 1994, "1994"), PeriodSearch.parse("1994"))
        assertEquals(1990 to 1999, PeriodSearch.parse("jaren 90")!!.let { it.from to it.to })
        assertEquals(1980 to 1989, PeriodSearch.parse("80s")!!.let { it.from to it.to })
        assertEquals(1990 to 1999, PeriodSearch.parse("1990s")!!.let { it.from to it.to })
        assertEquals(2010 to 2019, PeriodSearch.parse("jaren 10")!!.let { it.from to it.to })
        assertEquals("Mijn smaak says it like this", 2010 to 2019, PeriodSearch.parse("2010–2019")!!.let { it.from to it.to })
        assertEquals("de jaren 90", PeriodSearch.parse("jaren '90")!!.label)
        assertNull("a channel number or a title", PeriodSearch.parse("90"))
        assertNull(PeriodSearch.parse("Apollo 13"))
    }
}
