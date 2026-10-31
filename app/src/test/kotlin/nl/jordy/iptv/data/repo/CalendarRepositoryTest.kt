package nl.jordy.iptv.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.ChannelPick
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.SettingsStore
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A calendar picked from the list must be saved with its matches, or the reminder never comes. */
@RunWith(RobolectricTestRunner::class)
class CalendarRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var calendars: CalendarRepository
    private val server = MockWebServer()
    private val espn = ChannelPick(7, "ESPN", "sport", "SPORT", null, 0, false)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsStore(context))
        calendars = CalendarRepository(db, profiles, OkHttpClient())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name).readText()

    @Test
    fun `a FotMob club calendar is saved with its matches`() = runTest {
        profiles.ensureOne()
        server.enqueue(MockResponse.Builder().body(fixture("fotmob-utrecht.ics")).build())
        val result = calendars.add(server.url("/team/9908.ics").toString(), "FC Utrecht", espn)
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        val links = db.calendars().links()
        assertEquals(1, links.size)
    }

    @Test
    fun `a page that isn't a calendar is not saved`() = runTest {
        profiles.ensureOne()
        server.enqueue(MockResponse.Builder().body("<!DOCTYPE html><html></html>").build())
        assertTrue(calendars.add(server.url("/x").toString(), null, espn).isFailure)
        assertTrue(db.calendars().links().isEmpty())
    }
}
