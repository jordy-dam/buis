package nl.jordy.iptv.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Own guides: a channel from any category goes in with one long-press, stays where it's put, and per profile. */
@RunWith(RobolectricTestRunner::class)
class GuideRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var guides: GuideRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsStore(context))
        guides = GuideRepository(db, profiles)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `the first long-press creates Mijn gids, the second takes the channel out again`() = runTest {
        profiles.ensureOne()
        assertTrue(guides.toggleInOnlyGuide(101, "NPO 1", null))
        val g = guides.guides.first().single()
        assertEquals("Mijn gids", g.name)
        assertEquals(listOf(101), g.channels.map { it.streamId })

        assertFalse(guides.toggleInOnlyGuide(101, "NPO 1", null))
        assertEquals("no second guide made", 1, guides.guides.first().size)
        assertTrue(guides.guides.first().single().channels.isEmpty())
    }

    @Test
    fun `channels keep the order they were added in, and move one place at a time`() = runTest {
        profiles.ensureOne()
        val id = guides.create("Sport")
        listOf(1 to "Ziggo Sport", 2 to "ESPN", 3 to "Viaplay").forEach { (s, n) -> guides.toggle(id, s, n, null) }
        assertEquals(listOf(1, 2, 3), guides.channels(id).map { it.streamId })

        guides.move(id, 3, -1)
        assertEquals(listOf(1, 3, 2), guides.channels(id).map { it.streamId })
        guides.move(id, 1, -1)
        assertEquals("the top one can't go higher", listOf(1, 3, 2), guides.channels(id).map { it.streamId })
        guides.move(id, 2, 1)
        assertEquals("nor the last one lower", listOf(1, 3, 2), guides.channels(id).map { it.streamId })

        // A channel added after moving goes to the end, not between moved ones.
        guides.toggle(id, 4, "Eurosport", null)
        assertEquals(listOf(1, 3, 2, 4), guides.channels(id).map { it.streamId })
    }

    @Test
    fun `a blank name becomes Mijn gids, and a channel can sit in two guides`() = runTest {
        profiles.ensureOne()
        val mine = guides.create("   ")
        val sport = guides.create("Sport")
        guides.toggle(mine, 7, "Ziggo Sport", null)
        guides.toggle(sport, 7, "Ziggo Sport", null)
        val all = guides.guides.first()
        assertEquals(listOf("Mijn gids", "Sport"), all.map { it.name })
        assertTrue(all.all { g -> g.channels.any { it.streamId == 7 } })
        assertEquals(setOf(7), guides.channelIds.first())

        guides.delete(sport)
        assertEquals("deleting a guide leaves the other alone", listOf(7), guides.guides.first().single().channels.map { it.streamId })
    }

    @Test
    fun `each profile sees only its own guides`() = runTest {
        profiles.ensureOne()
        guides.toggleInOnlyGuide(101, "NPO 1", null)
        val bas = profiles.create("Bas", 1, kids = false, pinHash = null)
        profiles.select(bas)
        assertTrue(guides.guides.first().isEmpty())
        assertTrue(guides.channelIds.first().isEmpty())
        assertEquals("the guide still covers it for the household", listOf(101), db.guides().allChannelIds())
    }
}
