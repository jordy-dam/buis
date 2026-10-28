package nl.jordy.iptv.data.repo

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.SettingsStore
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamCredentials
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/** 20k films, one request each: the enricher must be gentle, resumable and only fetch what we show. */
@RunWith(RobolectricTestRunner::class)
class MovieEnricherTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var enricher: MovieEnricher
    private val requested = CopyOnWriteArrayList<Int>()
    private val failing = mutableSetOf<Int>()
    private lateinit var creds: XtreamCredentials

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = request.url.queryParameter("vod_id")!!.toInt()
                requested += id
                if (id in failing) return MockResponse.Builder().code(500).build()
                val genre = if (id % 2 == 0) "Thriller, Action" else "Komedie"
                return MockResponse.Builder().body("""{"info":{"genre":"$genre","cast":"A, B","plot":"P"},"movie_data":{"stream_id":$id}}""").build()
            }
        }
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        creds = XtreamCredentials(server.url("/").toString(), "u", "p")
        val profiles = ProfileRepository(db, SettingsStore(context))
        profiles.ensureOne() // NL
        enricher = MovieEnricher(XtreamClient(OkHttpClient()), CredentialStore(context), db, profiles)

        db.catalog().upsertCategories(listOf(CategoryEntity(ContentType.MOVIE, "nl", "NL", 0, 1, "NL"), CategoryEntity(ContentType.MOVIE, "us", "US", 1, 1, "US")))
        db.catalog().upsertMovies((1..6).map { MovieEntity("nl", it, "Film $it", null, null, null, "mp4", null, it, 1) } + MovieEntity("us", 99, "US film", null, null, null, "mp4", null, 0, 1))
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `fetches only films in the household's countries, and only once`() = runTest {
        assertEquals(6, enricher.run(creds, intervalMs = 0))
        assertEquals("never the US film", (1..6).toList(), requested.sorted())
        assertEquals("nothing left the second time", 0, enricher.run(creds, intervalMs = 0))
        assertEquals(6 to 6, enricher.progress.first())
    }

    @Test
    fun `a failing film doesn't stop the rest and is retried a day later`() = runTest {
        failing += 3
        enricher.run(creds, intervalMs = 0)
        assertEquals(5 to 6, enricher.progress.first())
        requested.clear()
        assertEquals("not again within a day", 0, enricher.run(creds, intervalMs = 0))
        failing.clear()
        val tomorrow = System.currentTimeMillis() + 25 * 3600_000L
        assertEquals(1, enricher.run(creds, intervalMs = 0, now = { tomorrow }))
        assertEquals(listOf(3), requested.toList())
    }

    @Test
    fun `requests are spaced out and wait while something plays`() = runTest {
        enricher.playing.value = 1
        val job = launch { enricher.run(creds, intervalMs = 250) }
        advanceTimeBy(5_000)
        assertTrue("nothing while playing", requested.isEmpty())
        enricher.playing.value = 0
        job.join()
        assertEquals(6, requested.size)
        assertTrue("about 250 ms apart on the test clock", testScheduler.currentTime >= 5_000 + 6 * 250)
    }

    @Test
    fun `the genre filter shows only matching films, and Alle stays complete`() = runTest {
        enricher.run(creds, intervalMs = 0)
        val dao = db.catalog()
        fun ids(source: PagingSource<Int, MovieEntity>) = kotlinx.coroutines.runBlocking {
            (source.load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page).data.map { it.streamId }
        }
        val (a, b) = Genres.patterns("Actie")
        assertEquals(listOf(2, 4, 6), ids(dao.moviesPagedByGenre("nl", a, b)))
        assertEquals((1..6).toList(), ids(dao.moviesPaged("nl")))
        assertEquals(listOf("Komedie", "Thriller", "Actie").toSet(), Genres.top(dao.movieGenres("nl").first()).toSet())
    }
}
