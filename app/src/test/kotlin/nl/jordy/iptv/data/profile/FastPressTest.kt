package nl.jordy.iptv.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.settings.SettingsStore
import nl.jordy.iptv.player.SleepTimer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A settings row that steps through choices (Slaaptimer: Uit, 30, 60 …) must count every OK, even
 * when the remote is pressed faster than a save takes. Each press used to step from the value on
 * screen, so two quick presses both went Uit → 30 and one was lost.
 */
@RunWith(RobolectricTestRunner::class)
class FastPressTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsStore(context))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `four quick presses on Slaaptimer step four choices`() = runTest {
        profiles.ensureOne()
        // All four in flight at once, as when OK is pressed four times before the first save is back.
        (1..4).map {
            async { profiles.update { it.copy(sleepMinutes = SleepTimer.nextChoice(it.sleepMinutes)) } }
        }.awaitAll()
        // Uit → 30 → 60 → 90 → 120.
        assertEquals(120, profiles.fresh().sleepMinutes)
    }

    @Test
    fun `two quick presses on a switch leave it where it was`() = runTest {
        profiles.ensureOne()
        val before = profiles.fresh().autoSwitch
        (1..2).map { async { profiles.update { it.copy(autoSwitch = !it.autoSwitch) } } }.awaitAll()
        assertEquals(before, profiles.fresh().autoSwitch)
    }
}
