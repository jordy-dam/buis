package nl.jordy.iptv.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A series gets its check only when every episode BUIS knows of is seen, per profile. */
@RunWith(RobolectricTestRunner::class)
class WatchedEpisodesTest {

    private fun series(id: Int, name: String) = nl.jordy.iptv.data.db.SeriesEntity("c$id", id, name, null, null, null, null, null, 2017, id, 1)
    private fun ep(id: Int, series: Int, n: Int) = EpisodeEntity(id, series, 1, n, "A$n", "mkv", null, null, 2700)

    @Test
    fun `a series is complete only with every episode watched, for that profile only`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.playback()
        db.episodes().upsert(listOf(ep(1, 10, 1), ep(2, 10, 2), ep(3, 20, 1)))
        // Series 10 in two languages (11 is the same title and year), 20 something else.
        db.catalog().upsertSeries(listOf(series(10, "Dark"), series(11, "Dark"), series(20, "Other")))

        dao.markEpisodeWatched(profileId = 1, episodeId = 1, at = 0)
        assertEquals("one of two episodes: not yet", emptyList<Int>(), dao.completeSeries(1).first())
        assertEquals(listOf(1), dao.watchedEpisodes(1).first())

        dao.markEpisodeWatched(1, 2, 0)
        dao.markEpisodeWatched(1, 2, 5) // seen again: still one row
        assertEquals("the other language version gets the check too", setOf(10, 11), dao.completeSeries(1).first().toSet())
        assertEquals(2, dao.watchedEpisodes(1).first().size)
        assertEquals("another profile saw nothing", emptyList<Int>(), dao.completeSeries(2).first())

        // A new episode in the list (next season added) takes the check away again.
        db.episodes().upsert(listOf(ep(4, 10, 3)))
        assertEquals(emptyList<Int>(), dao.completeSeries(1).first())

        dao.markEpisodeWatched(1, 999, 0) // an episode BUIS has no list for is skipped, not stored without a series
        assertEquals(2, dao.watchedEpisodes(1).first().size)
        db.close()
    }
}
