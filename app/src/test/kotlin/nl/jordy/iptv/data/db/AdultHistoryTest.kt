package nl.jordy.iptv.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An adult channel or film must never show up in what the household sees as "watched": the
 * history, the weekoverzicht, Verder kijken. Ordinary ones must.
 */
@RunWith(RobolectricTestRunner::class)
class AdultHistoryTest {
    private lateinit var db: AppDatabase
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.profiles().insert(ProfileEntity(1, "A", avatar = 0))
        db.catalog().upsertCategories(
            listOf(
                CategoryEntity(ContentType.LIVE, "news", "Nieuws", 0, 0, "NL"),
                CategoryEntity(ContentType.LIVE, "xxx", "Adult", 1, 0, null, adult = true),
                CategoryEntity(ContentType.MOVIE, "films", "Films", 0, 0, "NL"),
                CategoryEntity(ContentType.MOVIE, "xxxfilms", "Adult films", 1, 0, null, adult = true),
            ),
        )
        db.catalog().upsertLive(listOf(LiveChannelEntity("news", 1, "NPO 1", null, null, 0, 0), LiveChannelEntity("xxx", 2, "Hot", null, null, 1, 0)))
        db.catalog().upsertMovies(listOf(MovieEntity("films", 10, "Film", null, null, null, "mp4", null, 0, 0), MovieEntity("xxxfilms", 11, "Adult film", null, null, null, "mp4", null, 1, 0)))
    }

    @After
    fun tearDown() = db.close()

    private fun log(type: ContentType, id: Int) =
        WatchLogEntity(profileId = 1, type = type, itemId = id, title = "$id", detail = null, containerExtension = "", categoryId = null, startedAt = now, seconds = 600)

    @Test
    fun `adult channels and films are recognised, ordinary ones not`() = runTest {
        assertTrue(db.watchLog().isAdult(ContentType.LIVE, 2))
        assertTrue(db.watchLog().isAdult(ContentType.MOVIE, 11))
        assertFalse(db.watchLog().isAdult(ContentType.LIVE, 1))
        assertFalse(db.watchLog().isAdult(ContentType.MOVIE, 10))
        assertFalse("an episode id is no channel", db.watchLog().isAdult(ContentType.SERIES, 2))
    }

    @Test
    fun `the history leaves adult rows out and old ones are removed`() = runTest {
        listOf(log(ContentType.LIVE, 1), log(ContentType.LIVE, 2), log(ContentType.MOVIE, 10), log(ContentType.MOVIE, 11)).forEach { db.watchLog().insert(it) }

        assertEquals(setOf(1, 10), db.watchLog().since(1, 0).first().map { it.itemId }.toSet())
        db.watchLog().pruneAdult()
        assertEquals(setOf(1, 10), db.watchLog().since(1, 0).first().map { it.itemId }.toSet())
    }

    @Test
    fun `verder kijken leaves adult films out`() = runTest {
        db.playback().upsert(PlaybackPositionEntity(1, ContentType.MOVIE, 10, 60_000, 6_000_000, now))
        db.playback().upsert(PlaybackPositionEntity(1, ContentType.MOVIE, 11, 60_000, 6_000_000, now + 1))
        assertEquals(listOf(10), db.playback().recent(1, 10).first().map { it.itemId })
    }
}
