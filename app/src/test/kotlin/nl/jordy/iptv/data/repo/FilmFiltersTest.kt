package nl.jordy.iptv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A mood must find films in either spelling the provider uses, and never slip past the query's slots. */
class FilmFiltersTest {

    @Test
    fun `a mood covers its genres in Dutch and English`() {
        val licht = FilmFilters.patterns("Licht")
        assertEquals(listOf("%Komedie%", "%comedy%", "%Familie%", "%family%", "%Animatie%", "%animation%"), licht)
        val spannend = FilmFilters.patterns("Spannend")
        assertTrue("%Thriller%" in spannend && "%crime%" in spannend && "%action%" in spannend)
        assertTrue(spannend.size <= FilmFilters.SLOTS)
    }

    @Test
    fun `a single genre and Alle`() {
        assertEquals(listOf("%Drama%"), FilmFilters.patterns("Drama"))
        assertEquals(emptyList<String>(), FilmFilters.patterns(null))
    }

    @Test
    fun `slots are always full and padding repeats a real pattern`() {
        assertEquals(List(6) { "%Drama%" }, FilmFilters.slots("Drama"))
        assertEquals(6, FilmFilters.slots("Spannend").size)
        assertEquals(List(6) { "" }, FilmFilters.slots(null))
    }

    @Test
    fun `the length chip steps through and back to any length`() {
        assertEquals(100 * 60, FilmFilters.nextLength(0))
        assertEquals(0, FilmFilters.nextLength(120 * 60))
        assertEquals("Korter dan 100 min", FilmFilters.lengthLabel(100 * 60))
    }
}
