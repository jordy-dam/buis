package nl.jordy.iptv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class GenresTest {

    @Test
    fun `messy provider fields become clean Dutch chips`() {
        assertEquals(listOf("Drama", "Misdaad"), Genres.split("Drama, Misdaad"))
        assertEquals(listOf("Actie", "Avontuur"), Genres.split("Action & Adventure"))
        assertEquals(listOf("Komedie"), Genres.split("Comedy / Komedie"))
        assertEquals(listOf("Sci-Fi", "Fantasy"), Genres.split("Sci-Fi & Fantasy"))
        assertEquals(emptyList<String>(), Genres.split(null))
        assertEquals("fragments like 'TV' are noise", emptyList<String>(), Genres.split(" , TV"))
    }

    @Test
    fun `chips are the most common genres in the category`() {
        val raws = listOf("Drama, Misdaad", "Drama", "Comedy", "Komedie", "Drama, Komedie", null, "Reality")
        assertEquals(listOf("Drama", "Komedie", "Misdaad"), Genres.top(raws, max = 3))
    }

    @Test
    fun `a chip finds both spellings`() {
        assertEquals("%Komedie%" to "%comedy%", Genres.patterns("Komedie"))
        assertEquals("%Reality%" to "%Reality%", Genres.patterns("Reality"))
    }
}
