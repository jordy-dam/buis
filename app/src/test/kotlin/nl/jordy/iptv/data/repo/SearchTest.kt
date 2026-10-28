package nl.jordy.iptv.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.SearchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTest {

    @Test
    fun `typed text becomes a safe prefix query`() {
        assertEquals("npo* 1*", SearchRanking.matchExpression("NPO 1"))
        assertEquals("amelie*", SearchRanking.matchExpression("Amélie"))
        // Lower-cased, "OR" is just a word, so typed operators can't change the query.
        assertEquals("kill* or* bill*", SearchRanking.matchExpression("\"kill\" OR -bill"))
        assertNull(SearchRanking.matchExpression("  ¡! "))
    }

    @Test
    fun `dutch first, then titles starting with the query, each item once`() {
        val hits = listOf(
            SearchEntity("The Batman", "MOVIE", 1, "c", "US"),
            SearchEntity("Batman Begins", "MOVIE", 2, "c", "US"),
            SearchEntity("Batman Begins", "MOVIE", 2, "d", "US"), // same film in a second category
            SearchEntity("Batman", "MOVIE", 3, "c", "NL"),
        )
        val ranked = SearchRanking.rank(hits, "batman", setOf("NL"), perType = 10).getValue("MOVIE")
        assertEquals(listOf(3, 2, 1), ranked.map { it.itemId })
    }

    @Test
    fun `search over a 100k-item catalog answers well within half a second`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val words = listOf("Journaal", "Sport", "Film", "Nieuws", "Kids", "Docu", "Muziek", "Live", "Event", "Show")
        (0 until 100_000).chunked(5_000).forEach { chunk ->
            db.search().insert(chunk.map { i -> SearchEntity("${words[i % 10]} ${words[(i / 10) % 10]} $i", if (i % 3 == 0) "LIVE" else "MOVIE", i, "c", if (i % 7 == 0) "NL" else "US") })
        }
        val repo = SearchRepository(db)

        repo.search("journ", setOf("NL")) // warm-up
        val start = System.nanoTime()
        val results = repo.search("journaal sport", setOf("NL"))
        val ms = (System.nanoTime() - start) / 1_000_000

        assertTrue("took $ms ms", ms < 500)
        assertTrue("index finds matches", db.search().match("journaal* sport*", listOf("NL"), 10).isNotEmpty())
        // Catalog tables are empty in this test, so the hydrated results are too; timing covers the full path.
        assertTrue(results.isEmpty)
        db.close()
    }
}
