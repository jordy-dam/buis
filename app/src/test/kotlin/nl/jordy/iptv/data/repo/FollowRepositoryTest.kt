package nl.jordy.iptv.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.db.EpgChannelEntity
import nl.jordy.iptv.data.db.EpgProgrammeEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The guide search behind reminders: any spelling of the term, only the watching profile's terms. */
@RunWith(RobolectricTestRunner::class)
class FollowRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var follows: FollowRepository
    private val now = 1_800_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsStore(context))
        follows = FollowRepository(db, profiles)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun guide() {
        db.catalog().upsertLive(listOf(LiveChannelEntity("sport", 7, "Ziggo Viaplay", null, "viaplay.nl", 0, 1, archiveDays = 3)))
        db.epg().insertChannels(listOf(EpgChannelEntity(7, "viaplay.nl")))
        db.epg().insertProgrammes(
            listOf(
                EpgProgrammeEntity("viaplay.nl", now + 3600, now + 10800, "FORMULE 1: Grand Prix van Mexico", null),
                EpgProgrammeEntity("viaplay.nl", now - 7200, now - 3600, "Formule 1: Vrije training", null),
                EpgProgrammeEntity("viaplay.nl", now + 20000, now + 23000, "Darts", null),
            ),
        )
    }

    @Test
    fun `finds the upcoming race whatever the guide's capitals, not what's over`() = runTest {
        profiles.ensureOne()
        guide()
        follows.add("  formule  1 ")
        val m = follows.upcoming(now)
        assertEquals(listOf("FORMULE 1: Grand Prix van Mexico"), m.map { it.title })
        assertEquals(7, m.single().streamId)
        assertTrue(m.single().hasArchive)
    }

    @Test
    fun `a cup match is found by the club in the description`() = runTest {
        profiles.ensureOne()
        guide()
        db.epg().insertProgrammes(listOf(EpgProgrammeEntity("viaplay.nl", now + 7200, now + 14400, "Voetbal: KNVB Beker", "Live: FC Utrecht - Ajax")))
        follows.add("fc utrecht")
        assertEquals(listOf("Voetbal: KNVB Beker"), follows.upcoming(now).map { it.title })
    }

    @Test
    fun `the guide covers pinned categories and favourites, not only the preferred countries`() = runTest {
        db.catalog().upsertCategories(
            listOf(
                CategoryEntity(ContentType.LIVE, "nl", "NL Algemeen", 0, 1, country = "NL"),
                CategoryEntity(ContentType.LIVE, "ziggo", "Ziggo Kabel", 1, 1, country = null),
                CategoryEntity(ContentType.LIVE, "de", "DE Sport", 2, 1, country = "DE"),
            ),
        )
        db.catalog().upsertLive(
            listOf(
                LiveChannelEntity("nl", 1, "NPO 1", null, "npo1", 0, 1),
                LiveChannelEntity("ziggo", 2, "ESPN 1", null, "espn1", 0, 1),
                LiveChannelEntity("de", 3, "Sky DE", null, "sky", 0, 1),
                LiveChannelEntity("de", 4, "Sport1", null, "sport1", 1, 1),
            ),
        )
        val rows = db.catalog().liveForGuide(listOf("NL"), categoryIds = listOf("ziggo"), streamIds = listOf(4))
        assertEquals(setOf(1, 2, 4), rows.map { it.streamId }.toSet())
    }

    @Test
    fun `a kids profile is never sent to an adult channel`() = runTest {
        profiles.ensureOne()
        db.catalog().upsertCategories(listOf(CategoryEntity(ContentType.LIVE, "xxx", "ADULT", 0, 1, adult = true)))
        db.catalog().upsertLive(listOf(LiveChannelEntity("xxx", 9, "Adult", null, "adult.tv", 0, 1)))
        db.epg().insertChannels(listOf(EpgChannelEntity(9, "adult.tv")))
        db.epg().insertProgrammes(listOf(EpgProgrammeEntity("adult.tv", now + 600, now + 4200, "Late Sport", null)))
        val kid = profiles.create("Kim", 2, kids = true, pinHash = null)
        profiles.select(kid)
        follows.add("Sport")
        assertTrue(follows.upcoming(now).isEmpty())
    }

    @Test
    fun `another profile's terms don't remind this one`() = runTest {
        profiles.ensureOne()
        guide()
        follows.add("Darts")
        val bas = profiles.create("Bas", 1, kids = false, pinHash = null)
        profiles.select(bas)
        assertTrue(follows.upcoming(now).isEmpty())
    }
}
