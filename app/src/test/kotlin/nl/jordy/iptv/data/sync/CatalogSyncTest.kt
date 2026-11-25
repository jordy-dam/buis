package nl.jordy.iptv.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.jordy.iptv.data.db.AppDatabase
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

@RunWith(RobolectricTestRunner::class)
class CatalogSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var sync: CatalogSync
    private lateinit var creds: XtreamCredentials

    /** What the fake provider currently returns per action; tests swap these between syncs. */
    private val responses = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.url.queryParameter("action")
                return MockResponse.Builder().body(responses[action] ?: "[]").build()
            }
        }
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sync = CatalogSync(XtreamClient(OkHttpClient()), db, ApplicationProvider.getApplicationContext())
        creds = XtreamCredentials(server.url("/").toString(), "u", "p")
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun categories(vararg ids: String) = ids.joinToString(",", "[", "]") { """{"category_id":"$it","category_name":"Cat $it"}""" }

    private fun movies(count: Int, firstId: Int = 1, category: (Int) -> String = { "c${it % 10}" }) =
        (0 until count).joinToString(",", "[", "]") { i ->
            val id = firstId + i
            // Alternate string and number ids, and sprinkle in junk entries, like real panels.
            val idJson = if (i % 2 == 0) "$id" else "\"$id\""
            val junk = if (i % 1000 == 0) """{"name":"no id"},null,""" else ""
            """$junk{"stream_id":$idJson,"name":"Film $id","category_id":"${category(i)}","rating":"7"}"""
        }

    @Test
    fun `a 12k movie catalog lands in Room completely and only non-empty categories show`() = runTest {
        responses["get_vod_categories"] = categories(*Array(12) { "c$it" }) // c10, c11 stay empty
        responses["get_vod_streams"] = movies(12_000)
        val progress = mutableListOf<SyncProgress>()

        sync.run(creds) { progress += it }

        assertEquals(12_000, db.catalog().movieCount())
        assertEquals(10, db.catalog().movieCategories().first().size)
        val movieProgress = progress.filter { it.type == ContentType.MOVIE }.map { it.items }
        assertEquals("reports progress per batch", 12_000, movieProgress.last())
        assertTrue(movieProgress.size >= 12_000 / CatalogSync.BATCH_SIZE)
    }

    @Test
    fun `re-sync replaces the catalog instead of duplicating, and drops vanished items`() = runTest {
        responses["get_vod_categories"] = categories("c0")
        responses["get_vod_streams"] = movies(1_000) { "c0" }
        sync.run(creds)

        responses["get_vod_streams"] = movies(600, firstId = 401) { "c0" } // 1–400 removed, 401–1000 kept
        sync.run(creds)

        assertEquals(600, db.catalog().movieCount())
        assertEquals(null, db.catalog().movie(1))
        assertEquals("Film 1000", db.catalog().movie(1000)?.name)
    }

    @Test
    fun `an item listed in two categories shows in both`() = runTest {
        responses["get_live_categories"] = categories("a", "b")
        responses["get_live_streams"] = """[{"stream_id":1,"name":"X","category_id":"a"},{"stream_id":1,"name":"X","category_id":"b"}]"""
        sync.run(creds)

        assertEquals(1, db.catalog().liveInCategory("a").size)
        assertEquals(1, db.catalog().liveInCategory("b").size)
    }

    @Test
    fun `items without a category end up in Overig rather than disappearing`() = runTest {
        responses["get_live_categories"] = categories("a")
        responses["get_live_streams"] = """[{"stream_id":1,"name":"X","category_id":"a"},{"stream_id":2,"name":"Y","category_id":null}]"""
        sync.run(creds)

        val cats = db.catalog().liveCategories().first()
        assertEquals(listOf("Cat a", "Overig"), cats.map { it.name })
        assertEquals("Y", db.catalog().liveInCategory(CatalogSync.UNCATEGORIZED).single().name)
    }

    @Test
    fun `provider tags are cleaned at sync and land in the search index with their country`() = runTest {
        responses["get_live_categories"] = """[{"category_id":"1","category_name":"┃NL┃ ALGEMEEN"},{"category_id":"2","category_name":"┃MULTI / LINGO┃ APPLE TV+"}]"""
        responses["get_live_streams"] = """[{"stream_id":1,"name":"┃NL┃ NPO 1 FHD ⏺ʳᵉᶜ","category_id":"1"},{"stream_id":2,"name":"Apple Kids","category_id":"2"}]"""
        sync.run(creds)

        val cats = db.catalog().liveCategories().first().associateBy { it.id }
        assertEquals("ALGEMEEN" to "NL", cats.getValue("1").let { it.name to it.country })
        // The tag is drawn as a label next to the name, so it's stored apart from it.
        assertEquals("APPLE TV+" to "Multi", cats.getValue("2").let { it.name to it.badges })

        val npo = db.catalog().liveInCategory("1").single()
        assertEquals("NPO 1", npo.name)
        assertEquals("FHD", npo.badges)
        assertTrue(npo.catchup)

        val hit = db.search().match("npo*", listOf("NL"), 10).single()
        assertEquals(1 to "NL", hit.itemId to hit.country)
    }

    @Test
    fun `an empty or non-array answer does not wipe yesterday's catalog`() = runTest {
        responses["get_vod_categories"] = categories("c0")
        responses["get_vod_streams"] = movies(50) { "c0" }
        sync.run(creds)

        responses["get_vod_streams"] = "{}"
        sync.run(creds)
        assertEquals(50, db.catalog().movieCount())

        responses["get_vod_streams"] = "[]"
        sync.run(creds)
        assertEquals(50, db.catalog().movieCount())
        assertEquals(1, db.catalog().movieCategories().first().size)
    }

    // The diff: the provider sends the whole list every day, but only what changed may cost work,
    // and what changed must still end up right in the lists and in search.

    @Test
    fun `an unchanged list writes nothing on the next sync`() = runTest {
        responses["get_vod_categories"] = categories("c0")
        responses["get_vod_streams"] = movies(1_200) { "c0" }
        assertEquals(1_200, sync.run(creds).changed[ContentType.MOVIE])

        val again = sync.run(creds)

        assertEquals(0, again.changed[ContentType.MOVIE])
        assertEquals(0, again.removed[ContentType.MOVIE])
        assertEquals(1_200, db.catalog().movieCount())
        assertEquals(1, db.search().match("\"film 7\"", emptyList(), 10).count { it.itemId == 7 })
    }

    @Test
    fun `a renamed film is rewritten and found under its new name only`() = runTest {
        responses["get_vod_categories"] = categories("a", "b")
        responses["get_vod_streams"] = """[{"stream_id":1,"name":"Oldname","category_id":"a"},{"stream_id":1,"name":"Oldname","category_id":"b"},{"stream_id":2,"name":"Other","category_id":"a"}]"""
        sync.run(creds)

        // Only the entry in "a" changes; its twin in "b" keeps its own search entry.
        responses["get_vod_streams"] = """[{"stream_id":1,"name":"Newname","category_id":"a"},{"stream_id":1,"name":"Oldname","category_id":"b"},{"stream_id":2,"name":"Other","category_id":"a"}]"""
        val r = sync.run(creds)

        assertEquals(1, r.changed[ContentType.MOVIE])
        assertEquals(listOf("a"), db.search().match("newname", emptyList(), 10).map { it.categoryId })
        assertEquals(listOf("b"), db.search().match("oldname", emptyList(), 10).map { it.categoryId })
        assertEquals(1, db.search().match("other", emptyList(), 10).size)
    }

    @Test
    fun `a new film at the top is the only write, and the order follows the provider`() = runTest {
        responses["get_vod_categories"] = categories("c0")
        responses["get_vod_streams"] = movies(10, firstId = 2) { "c0" }
        sync.run(creds)

        // Panels renumber `num` for everything after an insert; that alone is no change.
        val withNew = """[{"stream_id":1,"name":"Brand new","category_id":"c0","num":1},""" + movies(10, firstId = 2) { "c0" }.drop(1)
        responses["get_vod_streams"] = withNew
        val r = sync.run(creds)

        assertEquals(1, r.changed[ContentType.MOVIE])
        assertEquals(0, db.catalog().movie(1)?.sortIndex)
        assertEquals(1, db.catalog().movie(2)?.sortIndex)
        assertEquals(10, db.catalog().movie(11)?.sortIndex)
    }

    @Test
    fun `a film gone at the provider leaves the list and search`() = runTest {
        responses["get_vod_categories"] = categories("c0")
        responses["get_vod_streams"] = """[{"stream_id":1,"name":"Stays","category_id":"c0"},{"stream_id":2,"name":"Leaves","category_id":"c0"}]"""
        sync.run(creds)

        responses["get_vod_streams"] = """[{"stream_id":1,"name":"Stays","category_id":"c0"}]"""
        val r = sync.run(creds)

        assertEquals(1 to 0, r.removed[ContentType.MOVIE] to r.changed[ContentType.MOVIE])
        assertEquals(null, db.catalog().movie(2))
        assertEquals(0, db.search().match("leaves", emptyList(), 10).size)
        assertEquals(1, db.search().match("stays", emptyList(), 10).size)
    }

    @Test
    fun `a film moving category is in the new one only`() = runTest {
        responses["get_vod_categories"] = categories("a", "b")
        responses["get_vod_streams"] = """[{"stream_id":1,"name":"Mover","category_id":"a"}]"""
        sync.run(creds)

        responses["get_vod_streams"] = """[{"stream_id":1,"name":"Mover","category_id":"b"}]"""
        sync.run(creds)

        assertEquals(listOf("b"), db.search().match("mover", emptyList(), 10).map { it.categoryId })
        assertEquals("b", db.catalog().movie(1)?.categoryId)
        assertEquals(1, db.catalog().movieCount())
    }
}
