package nl.jordy.iptv.data.repo

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.MovieInfoEntity
import nl.jordy.iptv.data.db.MovieTile
import nl.jordy.iptv.data.db.MovieVersionRow
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Providers list "Film", "┃NL-SUB┃ Film" and "┃MULTI┃ Film" separately; the household sees one film. */
@RunWith(RobolectricTestRunner::class)
class MovieVersionsTest {

    private fun movie(cat: String, id: Int, name: String, year: Int?, badges: String = "", sort: Int = id, tmdb: Int? = null) =
        MovieEntity(cat, id, name, null, null, year, "mp4", null, sort, 1, badges, tmdb)

    @Test
    fun `each version gets a distinct, readable name`() {
        val rows = listOf(
            MovieVersionRow(movie("a", 1, "Film", 2020), "NL"),
            MovieVersionRow(movie("a", 2, "Film", 2020, "NL sub"), "NL"),
            MovieVersionRow(movie("b", 3, "Film", 2020, "Multi,4K"), null),
            MovieVersionRow(movie("c", 4, "Film", 2020), null),
            MovieVersionRow(movie("d", 5, "Film", 2020), null),
        )
        val labels = MovieVersions.labels(rows)
        assertEquals(listOf("Origineel 1", "Nederlands ondertiteld", "Meerdere talen", "Origineel 2", "Origineel 3"), labels.map { it.language })
        assertEquals("quality goes on the second line", listOf("", "", "4K", "", ""), labels.map { it.detail })
    }

    @Test
    fun `versions with different titles say which title they are`() {
        val rows = listOf(
            MovieVersionRow(movie("a", 1, "The Croods 2: Een Nieuw Begin", 2020, "NL gesproken"), "NL"),
            MovieVersionRow(movie("a", 2, "The Croods: A New Age", 2020), "NL"),
        )
        assertEquals(
            listOf(
                MovieVersions.Label("Nederlands gesproken", "The Croods 2: Een Nieuw Begin"),
                MovieVersions.Label("Origineel", "The Croods: A New Age"),
            ),
            MovieVersions.labels(rows),
        )
    }

    @Test
    fun `provider tags read as plain Dutch, UK audio is English`() {
        val rows = listOf(
            MovieVersionRow(movie("a", 1, "Film", 2020, "UK audio,HDR"), null),
            MovieVersionRow(movie("a", 2, "Film", 2020, "NL audio"), "NL"),
        )
        assertEquals(
            listOf(MovieVersions.Label("Engels gesproken", "HDR"), MovieVersions.Label("Nederlands gesproken", "")),
            MovieVersions.labels(rows),
        )
    }

    @Test
    fun `Play starts the version watched last, else the one opened`() {
        assertEquals(3, MovieVersions.pickDefault(listOf(1, 2, 3), mapOf(1 to 100L, 3 to 200L), opened = 2))
        assertEquals(2, MovieVersions.pickDefault(listOf(1, 2, 3), emptyMap(), opened = 2))
        assertEquals("opened from elsewhere", 1, MovieVersions.pickDefault(listOf(1, 2), emptyMap(), opened = 9))
    }

    @Test
    fun `the grid shows one tile per title and year, remakes stay apart`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.catalog()
        dao.upsertCategories(listOf(CategoryEntity(ContentType.MOVIE, "c", "Netflix", 0, 1, "NL")))
        dao.upsertMovies(
            listOf(
                movie("c", 10, "Dune", 2021, sort = 0),
                movie("c", 11, "Dune", 2021, "NL sub", sort = 1),
                movie("c", 12, "dune", 2021, "Multi", sort = 2),
                movie("c", 13, "Dune", 1984, sort = 3),
                movie("c", 14, "Arrival", 2016, sort = 4),
            ),
        )
        fun load(source: PagingSource<Int, MovieTile>) = runBlocking {
            (source.load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page).data
        }
        val tiles = load(dao.movieTilesPaged("c"))
        assertEquals(listOf(10 to 3, 13 to 1, 14 to 1), tiles.map { it.movie.streamId to it.versions })
        assertEquals("all versions across the catalog", listOf(10, 11, 12), dao.movieVersions("Dune", 2021, null).map { it.movie.streamId })

        db.movieInfo().upsert(MovieInfoEntity(11, "Sci-Fi", null, null, null, null, 1))
        db.movieInfo().upsert(MovieInfoEntity(13, "Sci-Fi", null, null, null, null, 1))
        val (a, b) = Genres.patterns("Sci-Fi")
        assertEquals("genre filter grouped too", listOf(11, 13), load(dao.movieTilesPagedByGenre("c", a, b)).map { it.movie.streamId })
        db.close()
    }

    @Test
    fun `a Dutch and an English title of the same film are one tile through the TMDB id`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.catalog()
        dao.upsertCategories(listOf(CategoryEntity(ContentType.MOVIE, "d", "Disney+", 0, 1, "NL")))
        dao.upsertMovies(
            listOf(
                // As measured on the tv: the NL-dubbed and the original version under different titles.
                movie("d", 1, "The Croods 2: Een Nieuw Begin", 2020, "NL gesproken", sort = 0, tmdb = 529203),
                movie("d", 2, "The Croods: A New Age", 2020, sort = 1),
                movie("d", 3, "Garfield 2", 2006, sort = 2),
            ),
        )
        // The second version's id only comes with its details (get_vod_info).
        db.movieInfo().upsert(MovieInfoEntity(2, "Familie", null, null, null, null, 1, tmdbId = 529203))
        val tiles = runBlocking {
            (dao.movieTilesPaged("d").load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page).data
        }
        assertEquals(listOf(1 to 2, 3 to 1), tiles.map { it.movie.streamId to it.versions })
        assertEquals(listOf(1, 2), dao.movieVersions("The Croods 2: Een Nieuw Begin", 2020, 529203).map { it.movie.streamId })
        db.close()
    }
}
