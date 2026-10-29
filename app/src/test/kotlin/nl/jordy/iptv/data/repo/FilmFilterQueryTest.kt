package nl.jordy.iptv.data.repo

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.MovieInfoEntity
import nl.jordy.iptv.data.db.MovieTile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Stemming and lengte at Films must pick the right films from the database, alone and together. */
@RunWith(RobolectricTestRunner::class)
class FilmFilterQueryTest {

    private fun movie(id: Int, name: String) = MovieEntity("c", id, name, null, null, 2020, "mp4", null, id, 1)

    @Test
    fun `mood, length and both at once`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.catalog()
        dao.upsertMovies(listOf(movie(1, "Komedie kort"), movie(2, "Comedy lang"), movie(3, "Thriller kort"), movie(4, "Drama kort"), movie(5, "Zonder gegevens")))
        db.movieInfo().upsert(MovieInfoEntity(1, "Komedie", null, null, null, null, 1, durationSecs = 85 * 60))
        db.movieInfo().upsert(MovieInfoEntity(2, "Comedy, Romance", null, null, null, null, 1, durationSecs = 140 * 60))
        db.movieInfo().upsert(MovieInfoEntity(3, "Thriller", null, null, null, null, 1, durationSecs = 95 * 60))
        db.movieInfo().upsert(MovieInfoEntity(4, "Drama", null, null, null, null, 1, durationSecs = 90 * 60))

        fun ids(chip: String?, maxSecs: Int): List<Int> {
            val p = FilmFilters.slots(chip)
            val source = dao.movieTilesFiltered("c", chip != null, p[0], p[1], p[2], p[3], p[4], p[5], maxSecs)
            return runBlocking { (source.load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page<Int, MovieTile>).data.map { it.movie.streamId } }
        }

        assertEquals("Licht in both spellings", listOf(1, 2), ids("Licht", 0))
        assertEquals(listOf(3), ids("Spannend", 0))
        assertEquals("films without a known length don't pass", listOf(1, 3, 4), ids(null, 100 * 60))
        assertEquals(listOf(1), ids("Licht", 100 * 60))
        assertEquals(listOf(4), ids("Drama", 120 * 60))
        db.close()
    }
}
