package nl.jordy.iptv.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.settings.SettingsStore
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.XtreamClient
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Two people on one tv: what one marks or watches must never show up for the other. */
@RunWith(RobolectricTestRunner::class)
class ProfileIsolationTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var library: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsStore(context))
        library = LibraryRepository(XtreamClient(OkHttpClient()), CredentialStore(context), db, profiles)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `favourites and resume positions stay with the profile that made them`() = runTest {
        profiles.ensureOne()
        val anna = profiles.currentId()
        val bas = profiles.create("Bas", 1, kids = false, pinHash = null)

        library.toggleFavorite(ContentType.LIVE, 101, "NPO 1", null)
        library.reportProgress(anna, ContentType.MOVIE, 5001, positionMs = 600_000, durationMs = 6_000_000)

        profiles.select(bas)
        assertTrue("Bas has no favourites of Anna", library.favorites().first().isEmpty())
        assertEquals(null, library.savedPosition(ContentType.MOVIE, 5001))
        assertTrue(library.recentPositions(20).first().isEmpty())

        library.toggleFavorite(ContentType.LIVE, 202, "RTL 4", null)
        profiles.select(anna)
        assertEquals(listOf(101), library.favoriteIds(ContentType.LIVE).first())
        assertEquals(600_000L, library.savedPosition(ContentType.MOVIE, 5001)?.positionMs)
    }

    @Test
    fun `removing a profile takes its data with it and leaves the others alone`() = runTest {
        profiles.ensureOne()
        val anna = profiles.currentId()
        val bas = profiles.create("Bas", 1, kids = false, pinHash = null)
        profiles.select(bas)
        library.toggleFavorite(ContentType.MOVIE, 7, "Film", null)
        library.reportProgress(bas, ContentType.MOVIE, 7, 600_000, 6_000_000)
        profiles.select(anna)
        library.toggleFavorite(ContentType.MOVIE, 8, "Andere film", null)

        assertTrue(profiles.delete(bas))

        assertEquals(1, db.query("SELECT COUNT(*) FROM favorite", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, db.query("SELECT COUNT(*) FROM playback_position", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(listOf(8), library.favoriteIds(ContentType.MOVIE).first())
    }

    @Test
    fun `the last profile can't be removed, and a wiped household gets a profile back`() = runTest {
        profiles.ensureOne()
        assertFalse(profiles.delete(profiles.currentId()))

        db.clearAllTables() // logout
        profiles.ensureOne()
        assertEquals(1, profiles.all().size)
    }

    @Test
    fun `a new profile starts with the household's countries but its own settings after that`() = runTest {
        profiles.ensureOne()
        profiles.setPreferredCountries(setOf("NL", "BE"))
        val bas = profiles.create("Bas", 1, kids = false, pinHash = null)
        profiles.select(bas)
        assertEquals(setOf("NL", "BE"), profiles.currentPrefs().preferredCountries)

        profiles.setPreferredCountries(setOf("DE"))
        assertEquals(setOf("DE"), profiles.currentPrefs().preferredCountries)
        assertEquals("the first profile keeps its own", setOf("NL", "BE"), profiles.all().first { it.id != bas }.prefs.preferredCountries)
    }

    @Test
    fun `Recent toegevoegd shows newest films from preferred countries, adult ones only for grown-ups`() = runTest {
        db.catalog().upsertCategories(
            listOf(
                CategoryEntity(ContentType.MOVIE, "nl", "NL Films", 0, 1, "NL"),
                CategoryEntity(ContentType.MOVIE, "us", "US Films", 1, 1, "US"),
                CategoryEntity(ContentType.MOVIE, "nlx", "NL XXX", 2, 1, "NL", adult = true),
            ),
        )
        fun movie(cat: String, id: Int, added: Long) = MovieEntity(cat, id, "Film $id", null, null, null, "mp4", added, 0, 1)
        db.catalog().upsertMovies(listOf(movie("nl", 1, 100), movie("nl", 2, 300), movie("us", 3, 999), movie("nlx", 4, 500)))

        assertEquals(listOf(4, 2, 1), db.catalog().recentMovies(listOf("NL"), hideAdult = false, limit = 20).first().map { it.streamId })
        assertEquals(listOf(2, 1), db.catalog().recentMovies(listOf("NL"), hideAdult = true, limit = 20).first().map { it.streamId })
    }

    @Test
    fun `profile management is locked by grown-up PINs only once there is a kids profile`() {
        val dad = nl.jordy.iptv.data.db.ProfileEntity(1, "Papa", 0, pinHash = "h1")
        val mum = nl.jordy.iptv.data.db.ProfileEntity(2, "Mama", 1)
        val kid = nl.jordy.iptv.data.db.ProfileEntity(3, "Kind", 2, pinHash = "h3", kids = true)
        assertEquals(emptyList<String>(), ProfileRepository.managementLock(listOf(dad, mum)))
        assertEquals("the kid's own PIN doesn't unlock management", listOf("h1"), ProfileRepository.managementLock(listOf(dad, mum, kid)))
    }
}
